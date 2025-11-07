package id.my.agungdh.discordbotservermonitoring.scheduler;

import id.my.agungdh.discordbotservermonitoring.queue.WahaSendQueue;
import id.my.agungdh.discordbotservermonitoring.service.AbsenReminderState;
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
    private final AbsenReminderState state;
    private final List<String> phones;

    public AbsenReminder(
            WahaSendQueue queue,
            AbsenReminderState state,
            @Value("${waha.absen-reminder.phones:}") String phonesCsv
    ) {
        this.queue = queue;
        this.state = state;
        this.phones = Arrays.stream(phonesCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        log.info("[AbsenReminder] Loaded phones={}", phones);
    }

    @Scheduled(cron = "${waha.absen-reminder.cron:0 * * * * *}", zone = "Asia/Jakarta")
    public void sendAbsenReminder() {
        if (!state.isEnabledToday()) {
            log.debug("[AbsenReminder] SKIP: reminder off for today");
            return;
        }

        if (phones.isEmpty()) {
            log.warn("[AbsenReminder] SKIP: no phone numbers configured");
            return;
        }

        String text = "Sayang ❤️ jangan lupa absen ya, ini reminder dari pacarmu tercinta ❤️";
        queue.enqueueAll(phones, text);
        log.info("[AbsenReminder] Enqueued {} jobs for absen reminder", phones.size());
    }
}
