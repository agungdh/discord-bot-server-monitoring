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

    // throttle untuk alert spam saat masih down
    private final Map<String, Long> lastSent = new HashMap<>();

    // track sesi down per instance
    private static final class DownSession {
        final Instant start;
        final String alias;
        DownSession(Instant start, String alias) { this.start = start; this.alias = alias; }
    }
    private final Map<String, DownSession> downSessions = new HashMap<>();

    @Scheduled(fixedDelayString = "${polling.intervalMs:3000}")
    public void tick() {
        var results = prom.instantQuery(query);
        long nowEpoch = Instant.now().getEpochSecond();
        Instant now = Instant.ofEpochSecond(nowEpoch);

        for (PrometheusClient.ResultPoint r : results) {
            String instance = r.instance();
            String alias = r.alias();
            double value = r.value();
            boolean isDown = value >= 5.0;
            boolean hasSession = downSessions.containsKey(instance);

            // notifikasi saat masih down (throttled)
            if (isDown) {
                long last = lastSent.getOrDefault(instance, 0L);
                if (nowEpoch - last >= cooldownSec) {
                    ZonedDateTime tsLocal = now.atZone(ZoneId.systemDefault());
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
            }

            // mulai/tutup sesi
            if (isDown && !hasSession) {
                downSessions.put(instance, new DownSession(now, alias));
                log.info("Start DOWN session for {} (alias={}) at {}", instance, alias, now);
            } else if (!isDown && hasSession) {
                DownSession sess = downSessions.remove(instance);
                try {
                    File chart = buildOutageChart(instance, sess.alias, sess.start, now);

                    String duration = humanDuration(Duration.between(sess.start, now));
                    String caption = String.format(
                            "[PING RECOVERY] %s (%s) UP ✅\nDowntime: %s\nWindow: %s → %s (%s)",
                            instance,
                            (sess.alias == null || sess.alias.isBlank() ? "-" : sess.alias),
                            duration,
                            tsLocal(sess.start),
                            tsLocal(now),
                            ZoneId.systemDefault()
                    );

                    if (chart != null && chart.exists()) {
                        discordService.sendFile(guildId, channelId, chart, caption);
                        if (!chart.delete()) {
                            log.debug("Temp chart retained at {}", chart.getAbsolutePath());
                        }
                    } else {
                        discordService.sendMessage(guildId, channelId, caption + "\n(no chart data)");
                    }

                    // ✅ tambahan log di sini
                    log.info("End DOWN session for {} (alias={}) at {}. Duration {}",
                            instance,
                            sess.alias,
                            now,
                            duration);

                } catch (Exception e) {
                    log.error("Failed to build/send outage chart for {}: {}", instance, e.getMessage(), e);
                }
            }
        }
    }

    private File buildOutageChart(String instance, String alias, Instant start, Instant end) throws Exception {
        // ambil deret per menit
        var series = prom.rangeQuery(
                query,
                start,
                end,
                Duration.ofMinutes(1),
                instance,
                alias == null ? "" : alias
        );

        if (series == null || series.isEmpty()) {
            log.warn("No series data for {} (alias={}) between {} and {}", instance, alias, start, end);
            return null;
        }

        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());
        for (PrometheusClient.RangePoint p : series) {
            dataset.addValue(p.value(), "Errors", fmt.format(p.timestamp()));
        }

        String title = String.format("Ping Errors per Minute • %s (%s)", instance, (alias == null || alias.isBlank() ? "-" : alias));
        JFreeChart chart = ChartFactory.createLineChart(
                title,
                String.format("Time (%s)", ZoneId.systemDefault()),
                "Errors / min",
                dataset
        );

        File tmp = File.createTempFile("OutageChart_", ".png");
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
