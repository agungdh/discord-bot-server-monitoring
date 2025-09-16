package id.my.agungdh.discordbotservermonitoring.alert;

import id.my.agungdh.discordbotservermonitoring.client.PrometheusClient;
import id.my.agungdh.discordbotservermonitoring.service.DiscordService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AlertScheduler {
    private static final Logger log = LoggerFactory.getLogger(AlertScheduler.class);

    private final PrometheusClient prom;
    private final DiscordService discordService;

    @Value("${discord.guild-id}") String guildId;
    @Value("${discord.rto-alert-channel-id}") String channelId;

    @Value("${prometheus.query}") String query;
    @Value("${polling.cooldownSec:60}") long cooldownSec;

    // state: instance -> lastSentEpochSec
    private final Map<String, Long> lastSent = new HashMap<>();

    @Scheduled(fixedDelayString = "${polling.intervalMs:3000}")
    public void tick() {
        var results = prom.instantQuery(query);
        long now = Instant.now().getEpochSecond();

        for (var r : results) {
            if (r.value() >= 5.0) {
                String key = r.instance(); // per IP
                long last = lastSent.getOrDefault(key, 0L);
                if (now - last >= cooldownSec) {
                    Instant ts = Instant.ofEpochSecond(now);

                    String msg = String.format(
                            "[PING ALERT] Target=%s (%s) gagal %.0f kali/1m @ %s",
                            r.instance(), r.alias().isEmpty() ? "-" : r.alias(),
                            r.value(), ts);
                    log.warn(msg);
                    lastSent.put(key, now);

                    discordService.sendAlertEmbed(
                            guildId,
                            channelId,
                            r.instance(),
                            r.alias(),
                            r.value(),
                            ts
                    );
                }
            }
        }
    }
}
