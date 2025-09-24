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

    @Scheduled(cron = "0 * * * * *", zone = "Asia/Jakarta")
    public void sendTapReminder() {
        if (reminderPhones.isEmpty()) {
            log.warn("SKIP: waha.reminder.phones kosong/belum di-set");
            return;
        }
        String text = "halo halo ate imut :)";
        queue.enqueueAll(reminderPhones, text);
        log.info("Enqueued {} jobs", reminderPhones.size());
    }
}
