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

    // throttle untuk alert spam per instance saat masih down
    private final Map<String, Long> lastSent = new HashMap<>();

    // ===== Global down session (bukan per IP) =====
    private static final class GlobalDownSession {
        final Instant start;
        final Set<String> instances = new HashSet<>();
        final Map<String, String> aliasByInstance = new HashMap<>();
        GlobalDownSession(Instant start) { this.start = start; }
    }
    private GlobalDownSession session = null;

    // Untuk syarat "up jika 1 menit tidak ada yang down"
    private Instant clearSince = null;

    @Scheduled(fixedDelayString = "${polling.intervalMs:3000}")
    public void tick() {
        var results = prom.instantQuery(query);
        long nowEpoch = Instant.now().getEpochSecond();
        Instant now = Instant.ofEpochSecond(nowEpoch);

        boolean anyDown = false;

        // ===== proses setiap target (dan kirim alert embed per instance dg cooldown) =====
        for (PrometheusClient.ResultPoint r : results) {
            String instance = r.instance();
            String alias = r.alias();
            double value = r.value();
            boolean isDown = value >= 5.0;

            if (isDown) {
                anyDown = true;

                // alert throttled per instance
                long last = lastSent.getOrDefault(instance, 0L);
                if (nowEpoch - last >= cooldownSec) {
                    var tsLocal = now.atZone(ZoneId.systemDefault());
                    String msg = String.format(
                            "[PING ALERT] Target=%s (%s) gagal %.0f kali/1m @ %s",
                            instance,
                            alias == null || alias.isBlank() ? "-" : alias,
                            value,
                            tsLocal.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"))
                    );
                    log.warn(msg);
                    lastSent.put(instance, nowEpoch);
                    discordService.sendAlertEmbed(guildId, channelId, instance, alias, value, now);
                }

                // catat ke global session jika sedang aktif
                if (session != null) {
                    session.instances.add(instance);
                    session.aliasByInstance.put(instance, alias == null ? "" : alias);
                }
            }
        }

        // ===== state machine global session =====
        if (session == null) {
            // belum ada sesi — mulai jika ada yang down
            if (anyDown) {
                session = new GlobalDownSession(now);
                // catat semua instance yang down pada tick ini
                for (var r : results) {
                    if (r.value() >= 5.0) {
                        session.instances.add(r.instance());
                        session.aliasByInstance.put(r.instance(), r.alias() == null ? "" : r.alias());
                    }
                }
                log.info("Start GLOBAL DOWN session at {}", now);
                // saat masih ada yang down, tidak ada clearSince
                clearSince = null;
            }
        } else {
            // sudah dalam sesi
            if (anyDown) {
                // masih down: reset clearSince
                clearSince = null;
                // akumulasi target down yang baru muncul
                for (var r : results) {
                    if (r.value() >= 5.0) {
                        session.instances.add(r.instance());
                        session.aliasByInstance.put(r.instance(), r.alias() == null ? "" : r.alias());
                    }
                }
            } else {
                // tidak ada yang down saat ini
                if (clearSince == null) {
                    clearSince = now; // mulai hitung periode bersih
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

        // Kunci = timestamp (minute step dari Prometheus), nilai = total error di menit itu
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
