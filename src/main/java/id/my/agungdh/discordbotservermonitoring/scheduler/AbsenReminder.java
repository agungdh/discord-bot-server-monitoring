package id.my.agungdh.discordbotservermonitoring.scheduler;

import id.my.agungdh.discordbotservermonitoring.queue.WahaSendQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class AbsenReminder {

    private static final Logger log = LoggerFactory.getLogger(AbsenReminder.class);

    private final WahaSendQueue queue;
    private final boolean enabled;
    private final List<String> phones;

    public AbsenReminder(
            WahaSendQueue queue,
            @Value("${waha.absen-reminder.enabled:false}") boolean enabled,
            @Value("${waha.absen-reminder.phones:}") String phonesCsv
    ) {
        this.queue = queue;
        this.enabled = enabled;
        this.phones = Arrays.stream(phonesCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        log.info("[AbsenReminder] enabled={} phones={}", enabled, this.phones);
    }

    /**
     * Kirim pesan tiap 1 menit (cron default dari config).
     * Cron bisa diubah di application.yml:
     *   waha.absen-reminder.cron: "0 * * * * *"
     */
    @Scheduled(cron = "${waha.absen-reminder.cron:0 * * * * *}", zone = "Asia/Jakarta")
    public void sendAbsenReminder() {
        if (!enabled) {
            log.debug("[AbsenReminder] SKIP: disabled via config (waha.absen-reminder.enabled=false)");
            return;
        }

        if (phones.isEmpty()) {
            log.warn("[AbsenReminder] SKIP: waha.absen-reminder.phones kosong/belum di-set");
            return;
        }

        String text = "Sayang ❤️ jangan lupa absen ya, ini reminder dari my prince tercinta <3";

        queue.enqueueAll(phones, text);
        log.info("[AbsenReminder] Enqueued {} jobs for absen reminder", phones.size());
    }
}
