package id.my.agungdh.discordbotservermonitoring.scheduler;

import id.my.agungdh.discordbotservermonitoring.DTO.waha.SendTextResponse;
import id.my.agungdh.discordbotservermonitoring.service.WahaService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class WakeUpReminder {

    private final WahaService wahaService;
    private final List<String> reminderPhones;

    public WakeUpReminder(
            WahaService wahaService,
            @Value("${waha.reminder.phones:}") String phonesCsv
    ) {
        this.wahaService = wahaService;
        this.reminderPhones = Arrays.stream(phonesCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /** Kirim tiap menit dari 19:30–19:34 WIB */
    @Scheduled(cron = "0 30-34 19 * * *", zone = "Asia/Jakarta")
    public void sendTapReminder() {
        if (reminderPhones.isEmpty()) {
            System.out.println("[WakeUpReminder] SKIP: waha.reminder.phones kosong/belum di-set");
            return;
        }

        String text = "Tap hari! 🔔";
        for (String phone : reminderPhones) {
            SendTextResponse res = wahaService.sendText(phone, text);
            System.out.println("[WakeUpReminder] Sent to " + phone + " -> " + res);
        }
    }
}
