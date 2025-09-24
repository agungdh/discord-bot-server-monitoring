package id.my.agungdh.discordbotservermonitoring.scheduler;

import id.my.agungdh.discordbotservermonitoring.client.PrometheusClient;
import id.my.agungdh.discordbotservermonitoring.service.DiscordService;
import lombok.RequiredArgsConstructor;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.data.category.DefaultCategoryDataset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
@RequiredArgsConstructor
public class AlertScheduler {
    private static final Logger log = LoggerFactory.getLogger(AlertScheduler.class);
    // ====== Pindahan dari env ke constant ======
    // Query Prometheus
    private static final String QUERY = """
            sum by (instance, alias) (
              count_over_time(probe_success{job="blackbox_ping"}[1m])
              - sum_over_time(probe_success{job="blackbox_ping"}[1m])
            )
            """;
    // Interval polling (ms) — jadi compile-time constant agar bisa dipakai di @Scheduled
    private static final long POLL_INTERVAL_MS = 3000L;
    // Cooldown alert (detik)
    private static final long COOLDOWN_SEC = 60L;
    private final PrometheusClient prom;
    private final DiscordService discordService;
    // ====== IDs tetap lewat env/properties (kalau mau, ini juga bisa dijadikan constant) ======
    @Value("${discord.guild-id}")
    String guildId;
    @Value("${discord.rto-alert-channel-id}")
    String channelId;
    private GlobalDownSession session = null;
    private Instant clearSince = null;
    private long lastGlobalAlertEpoch = 0L;

    private static String tsLocal(Instant ts) {
        return ts.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    private static String humanDuration(Duration d) {
        long h = d.toHours();
        long m = d.toMinutesPart();
        long s = d.toSecondsPart();
        if (h > 0) return String.format("%dh %02dm %02ds", h, m, s);
        if (m > 0) return String.format("%dm %02ds", m, s);
        return String.format("%ds", s);
    }

    // pakai fixedDelay = constant (bukan fixedDelayString dari env)
    @Scheduled(fixedDelay = POLL_INTERVAL_MS)
    public void tick() {
        var results = prom.instantQuery(QUERY);
        long nowEpoch = Instant.now().getEpochSecond();
        Instant now = Instant.ofEpochSecond(nowEpoch);

        // kumpulkan target down
        List<PrometheusClient.ResultPoint> downs = new ArrayList<>();
        for (PrometheusClient.ResultPoint r : results) {
            if (r.value() >= 5.0) downs.add(r);
        }
        boolean anyDown = !downs.isEmpty();

        // ===== GLOBAL SESSION STATE =====
        if (session == null) {
            if (anyDown) {
                session = new GlobalDownSession(now);
                for (var r : downs) {
                    session.instances.add(r.instance());
                    session.aliasByInstance.put(r.instance(), r.alias() == null ? "" : r.alias());
                }
                log.info("Start GLOBAL DOWN session at {}", now);
                clearSince = null;
            }
        } else {
            if (anyDown) {
                for (var r : downs) {
                    session.instances.add(r.instance());
                    session.aliasByInstance.put(r.instance(), r.alias() == null ? "" : r.alias());
                }
                clearSince = null; // masih down → reset
            } else {
                if (clearSince == null) clearSince = now;
                if (Duration.between(clearSince, now).compareTo(Duration.ofMinutes(1)) >= 0) {
                    try {
                        Instant start = session.start;
                        File chart = buildOutageChartPerTarget(session, start, now);

                        String duration = humanDuration(Duration.between(start, now));
                        String caption = String.format(
                                "[PING RECOVERY] ALL TARGETS UP ✅\nDowntime: %s\nWindow: %s → %s (%s)\nTargets involved: %s",
                                duration,
                                tsLocal(start),
                                tsLocal(now),
                                ZoneId.systemDefault(),
                                String.join(", ", session.instances)
                        );

                        if (chart != null && chart.exists()) {
                            discordService.sendFile(guildId, channelId, chart, caption);
                            if (!chart.delete()) {
                                log.debug("Temp chart retained at {}", chart.getAbsolutePath());
                            }
                        } else {
                            discordService.sendMessage(guildId, channelId, caption + "\n(no chart data)");
                        }

                        log.info("End GLOBAL DOWN session at {} (duration {})", now, duration);
                    } catch (Exception e) {
                        log.error("Failed to build/send outage chart: {}", e.getMessage(), e);
                    } finally {
                        session = null;
                        clearSince = null;
                    }
                }
            }
        }

        // ===== GLOBAL ALERT MESSAGE =====
        if (anyDown && nowEpoch - lastGlobalAlertEpoch >= COOLDOWN_SEC) {
            StringBuilder sb = new StringBuilder();
            sb.append("🛑 **GLOBAL PING ALERT**\n");
            sb.append("Waktu: ").append(tsLocal(now)).append("\n");
            sb.append("Daftar target (gagal/1m):\n");
            downs.sort((a, b) -> Double.compare(b.value(), a.value()));
            for (var r : downs) {
                String alias = (r.alias() == null || r.alias().isBlank()) ? "-" : r.alias();
                sb.append(String.format("- `%s` (%s): %d/1m\n", r.instance(), alias, (int) r.value()));
            }
            discordService.sendMessage(guildId, channelId, sb.toString());
            lastGlobalAlertEpoch = nowEpoch;
        }
    }

    /**
     * Chart: 1 garis per target (instance).
     */
    private File buildOutageChartPerTarget(GlobalDownSession sess, Instant start, Instant end) throws Exception {
        if (sess.instances.isEmpty()) return null;

        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

        for (String inst : sess.instances) {
            String alias = sess.aliasByInstance.getOrDefault(inst, "");
            var series = prom.rangeQuery(QUERY, start, end, Duration.ofMinutes(1), inst, alias);
            String seriesName = alias.isBlank() ? inst : alias + " (" + inst + ")";
            for (PrometheusClient.RangePoint p : series) {
                dataset.addValue(p.value(), seriesName, fmt.format(p.timestamp()));
            }
        }

        if (dataset.getRowCount() == 0) return null;

        JFreeChart chart = ChartFactory.createLineChart(
                "Ping Errors per Minute (per target)",
                String.format("Time (%s)", ZoneId.systemDefault()),
                "Errors / min",
                dataset
        );

        File tmp = File.createTempFile("OutageChart_PerTarget_", ".png");
        ChartUtils.saveChartAsPNG(tmp, chart, 1000, 500);
        return tmp;
    }

    // ===== Global down session =====
    private static final class GlobalDownSession {
        final Instant start;
        final Set<String> instances = new HashSet<>();
        final Map<String, String> aliasByInstance = new HashMap<>();

        GlobalDownSession(Instant start) {
            this.start = start;
        }
    }
}
