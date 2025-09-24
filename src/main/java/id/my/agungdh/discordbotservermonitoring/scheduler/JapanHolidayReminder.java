// src/main/java/id/my/agungdh/discordbotservermonitoring/scheduler/JapanHolidayReminder.java
package id.my.agungdh.discordbotservermonitoring.scheduler;

import id.my.agungdh.discordbotservermonitoring.queue.WahaSendQueue;
import id.my.agungdh.discordbotservermonitoring.service.JapanHolidayService;
import id.my.agungdh.discordbotservermonitoring.service.JapanHolidayService.Holiday;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Component
public class JapanHolidayReminder {

    private static final Logger log = LoggerFactory.getLogger(JapanHolidayReminder.class);

    private static final ZoneId LOCAL_ZONE = ZoneId.of("Asia/Jakarta");
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd (EEE)", Locale.ENGLISH);

    private final JapanHolidayService holidayService;
    private final WahaSendQueue queue;
    private final List<String> phones;

    public JapanHolidayReminder(
            JapanHolidayService holidayService,
            WahaSendQueue queue,
            @Value("${waha.jp-holiday-reminder.phones:}") String phonesCsv
    ) {
        this.holidayService = holidayService;
        this.queue = queue;
        this.phones = Arrays.stream(phonesCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * Cek tiap hari jam 08:00 WIB:
     * - Jika BESOK libur Jepang (H-1) => kirim reminder H-1
     * - Jika HARI INI libur Jepang (H)  => kirim reminder hari-H
     */
    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Jakarta")
    public void dailyJapanHolidayChecks() {
        if (phones.isEmpty()) {
            log.warn("[JapanHolidayReminder] SKIP: waha.jp-holiday-reminder.phones kosong/belum di-set");
            return;
        }

        LocalDate today = LocalDate.now(LOCAL_ZONE);
        LocalDate tomorrow = today.plusDays(1);

        // H-1
        holidayService.getHoliday(tomorrow).ifPresent(h -> {
            String msg = buildHMinusOneMessage(h);
            queue.enqueueAll(phones, msg);
            log.info("[JapanHolidayReminder] Enqueued H-1 for {} ({}) -> {} recipients",
                    h.name(), h.date().format(DATE_FMT), phones.size());
        });

        // Hari-H
        holidayService.getHoliday(today).ifPresent(h -> {
            String msg = buildTodayMessage(h);
            queue.enqueueAll(phones, msg);
            log.info("[JapanHolidayReminder] Enqueued DAY-OF for {} ({}) -> {} recipients",
                    h.name(), h.date().format(DATE_FMT), phones.size());
        });
    }

    /* ================= Helpers ================= */

    private String buildHMinusOneMessage(Holiday h) {
        return "Reminder H-1 🇯🇵\n"
                + "Besok libur Jepang: " + h.name() + "\n"
                + "Tanggal: " + h.date().format(DATE_FMT) + "\n"
                + "Siapkan jadwal ya 😊";
    }

    private String buildTodayMessage(Holiday h) {
        return "Hari ini libur Jepang 🇯🇵\n"
                + h.name() + "\n"
                + "Tanggal: " + h.date().format(DATE_FMT) + "\n"
                + "Selamat berlibur! 🎌";
    }
}
