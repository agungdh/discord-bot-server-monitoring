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
public class WakeUpReminder {

    private static final Logger log = LoggerFactory.getLogger(WakeUpReminder.class);

    private final WahaSendQueue queue;
    private final List<String> reminderPhones;

    public WakeUpReminder(
            WahaSendQueue queue,
            @Value("${waha.reminder.phones:}") String phonesCsv
    ) {
        this.queue = queue;
        this.reminderPhones = Arrays.stream(phonesCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * Tiap 10 menit antara jam 09:00–09:59 WITA (Senin–Jumat)
     */
    @Scheduled(cron = "0 0/10 9 * * 1-5", zone = "Asia/Makassar")
    public void remindGirlfriend() {
        if (reminderPhones.isEmpty()) {
            log.warn("SKIP: waha.reminder.phones kosong/belum di-set");
            return;
        }
        String text = "Sayang ❤️ jangan lupa ya, bangunin aku jam 10 nanti!";
        queue.enqueueAll(reminderPhones, text);
        log.info("Enqueued {} jobs for reminder before 10", reminderPhones.size());
    }

    /**
     * Tepat jam 10:00 WITA (Senin–Jumat)
     */
    @Scheduled(cron = "0 0 10 * * 1-5", zone = "Asia/Makassar")
    public void finalWakeUpCall() {
        if (reminderPhones.isEmpty()) {
            log.warn("SKIP: waha.reminder.phones kosong/belum di-set");
            return;
        }
        String text = "Udah jam 10! 📞 Tolong telpon ke nomor utamaku ya 😘";
        queue.enqueueAll(reminderPhones, text);
        log.info("Enqueued {} jobs for FINAL wakeup call", reminderPhones.size());
    }
}
