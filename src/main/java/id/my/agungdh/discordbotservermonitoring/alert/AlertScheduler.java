package id.my.agungdh.discordbotservermonitoring.alert;

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
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
@RequiredArgsConstructor
public class AlertScheduler {
    private static final Logger log = LoggerFactory.getLogger(AlertScheduler.class);

    private final PrometheusClient prom;
    private final DiscordService discordService;

    @Value("${discord.guild-id}") String guildId;
    @Value("${discord.rto-alert-channel-id}") String channelId;

    @Value("${prometheus.query}") String query; // error/1m >=5 => alert
    @Value("${polling.cooldownSec:60}") long cooldownSec;

    // ===== Global down session (bukan per IP) =====
    private static final class GlobalDownSession {
        final Instant start;
        final Set<String> instances = new HashSet<>();
        final Map<String, String> aliasByInstance = new HashMap<>();
        GlobalDownSession(Instant start) { this.start = start; }
    }
    private GlobalDownSession session = null;

    // "up jika 1 menit tidak ada yang down"
    private Instant clearSince = null;

    // throttle untuk pesan "GLOBAL DOWN" supaya tidak spam
    private long lastGlobalAlertEpoch = 0L;

    @Scheduled(fixedDelayString = "${polling.intervalMs:3000}")
    public void tick() {
        var results = prom.instantQuery(query);
        long nowEpoch = Instant.now().getEpochSecond();
        Instant now = Instant.ofEpochSecond(nowEpoch);

        // Kumpulkan target yang down pada tick ini
        List<PrometheusClient.ResultPoint> downs = new ArrayList<>();
        for (PrometheusClient.ResultPoint r : results) {
            if (r.value() >= 5.0) {
                downs.add(r);
            }
        }
        boolean anyDown = !downs.isEmpty();

        // ===== GLOBAL SESSION STATE MACHINE =====
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
                // akumulasi target yang down baru
                for (var r : downs) {
                    session.instances.add(r.instance());
                    session.aliasByInstance.put(r.instance(), r.alias() == null ? "" : r.alias());
                }
                // masih down → reset clearSince
                clearSince = null;
            } else {
                // tidak ada yang down saat ini → mulai/lanjut hitung bersih
                if (clearSince == null) {
                    clearSince = now;
                }
                // jika sudah bersih >= 1 menit → recovery
                if (Duration.between(clearSince, now).compareTo(Duration.ofMinutes(1)) >= 0) {
                    try {
                        Instant start = session.start;
                        File chart = buildOutageChartGlobal(session, start, now);

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
                        log.error("Failed to build/send GLOBAL outage chart: {}", e.getMessage(), e);
                    } finally {
                        // tutup sesi & reset state
                        session = null;
                        clearSince = null;
                    }
                }
            }
        }

        // ===== KIRIM GLOBAL ALERT (gabungan) SAAT SEDANG DOWN =====
        if (anyDown) {
            if (nowEpoch - lastGlobalAlertEpoch >= cooldownSec) {
                StringBuilder sb = new StringBuilder();
                sb.append("🛑 **GLOBAL PING ALERT** — target down terdeteksi\n");
                sb.append("Waktu: ").append(tsLocal(now)).append("\n");
                sb.append("Daftar target (gagal/1m):\n");
                // urutkan yang paling parah di atas
                downs.sort((a, b) -> Double.compare(b.value(), a.value()));
                for (var r : downs) {
                    String alias = (r.alias() == null || r.alias().isBlank()) ? "-" : r.alias();
                    sb.append(String.format("- `%s` (%s): %d/1m\n", r.instance(), alias, (int) r.value()));
                }
                discordService.sendMessage(guildId, channelId, sb.toString());
                lastGlobalAlertEpoch = nowEpoch;
            }
        }
    }

    /**
     * Bangun 1 chart total error / menit dari start→end dengan menjumlahkan
     * semua series (per instance) yang terlibat dalam sesi.
     */
    private File buildOutageChartGlobal(GlobalDownSession sess, Instant start, Instant end) throws Exception {
        if (sess.instances.isEmpty()) {
            log.warn("No instances recorded in session between {} and {}", start, end);
            return null;
        }

        // Kunci = timestamp, nilai = total error pada menit ts
        Map<Instant, Double> totalByTs = new TreeMap<>();

        for (String inst : sess.instances) {
            String alias = sess.aliasByInstance.getOrDefault(inst, "");
            var series = prom.rangeQuery(
                    query,
                    start,
                    end,
                    Duration.ofMinutes(1),
                    inst,
                    alias
            );
            for (PrometheusClient.RangePoint p : series) {
                totalByTs.merge(p.timestamp(), p.value(), Double::sum);
            }
        }

        if (totalByTs.isEmpty()) {
            log.warn("No range data returned for any instance in session.");
            return null;
        }

        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());
        for (Map.Entry<Instant, Double> e : totalByTs.entrySet()) {
            dataset.addValue(e.getValue(), "Total Errors", fmt.format(e.getKey()));
        }

        JFreeChart chart = ChartFactory.createLineChart(
                "Total Ping Errors per Minute (GLOBAL)",
                String.format("Time (%s)", ZoneId.systemDefault()),
                "Errors / min",
                dataset
        );

        File tmp = File.createTempFile("OutageChart_GLOBAL_", ".png");
        ChartUtils.saveChartAsPNG(tmp, chart, 1000, 500);
        return tmp;
    }

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
}
