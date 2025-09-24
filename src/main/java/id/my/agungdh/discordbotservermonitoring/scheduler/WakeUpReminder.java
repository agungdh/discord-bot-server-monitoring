package id.my.agungdh.discordbotservermonitoring.scheduler;

import id.my.agungdh.discordbotservermonitoring.queue.WahaSendQueue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class WakeUpReminder {

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

    /** Tiap menit 19:30–19:34 WIB: masukkan job ke queue (worker kirim satu-satu) */
    @Scheduled(cron = "0 42-44 19 * * *", zone = "Asia/Jakarta")
    public void sendTapReminder() {
        if (reminderPhones.isEmpty()) {
            System.out.println("[WakeUpReminder] SKIP: waha.reminder.phones kosong/belum di-set");
            return;
        }
        String text = "hey hey anak manis...";
        queue.enqueueAll(reminderPhones, text);
        System.out.println("[WakeUpReminder] Enqueued " + reminderPhones.size() + " jobs");
    }
}
