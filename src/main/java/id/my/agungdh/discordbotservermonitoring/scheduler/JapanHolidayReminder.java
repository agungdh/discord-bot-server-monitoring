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

import java.time.DayOfWeek;
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
            DateTimeFormatter.ofPattern("d MMMM yyyy (EEEE)", Locale.forLanguageTag("id-ID"));

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
     * Jalan jam 06:00, 09:00, 12:00, dan 15:00 WIB SETIAP HARI.
     * - H-1: kirim kalau BESOK adalah weekday (Mon–Fri). → Minggu → Senin tetap terkirim.
     * - Hari-H: kirim hanya kalau HARI INI weekday (Mon–Fri) agar tidak spam di weekend.
     */
    @Scheduled(cron = "0 0 6,9,12,15 * * *", zone = "Asia/Jakarta")
    public void dailyJapanHolidayChecks() {
        if (phones.isEmpty()) {
            log.warn("[JapanHolidayReminder] SKIP: waha.jp-holiday-reminder.phones kosong/belum di-set");
            return;
        }

        LocalDate today = LocalDate.now(LOCAL_ZONE);
        LocalDate tomorrow = today.plusDays(1);

        boolean todayWeekend = isWeekend(today);
        boolean tomorrowWeekend = isWeekend(tomorrow);

        // H-1: kirim jika BESOK weekday (Minggu → Senin akan mengirim)
        if (!tomorrowWeekend) {
            holidayService.getHoliday(tomorrow).ifPresent(h -> {
                String msg = buildHMinusOneMessage(h);
                queue.enqueueAll(phones, msg);
                log.info("[JapanHolidayReminder] Enqueued H-1 for {} ({}) -> {} recipients",
                        h.name(), h.date().format(DATE_FMT), phones.size());
            });
        } else {
            log.debug("[JapanHolidayReminder] SKIP H-1 karena besok weekend ({})", tomorrow.getDayOfWeek());
        }

        // Hari-H: kirim hanya kalau HARI INI weekday
        if (!todayWeekend) {
            holidayService.getHoliday(today).ifPresent(h -> {
                String msg = buildTodayMessage(h);
                queue.enqueueAll(phones, msg);
                log.info("[JapanHolidayReminder] Enqueued DAY-OF for {} ({}) -> {} recipients",
                        h.name(), h.date().format(DATE_FMT), phones.size());
            });
        } else {
            log.debug("[JapanHolidayReminder] SKIP Day-Of karena hari ini weekend ({})", today.getDayOfWeek());
        }
    }

    /* ================= Helpers ================= */

    private static boolean isWeekend(LocalDate d) {
        DayOfWeek w = d.getDayOfWeek();
        return w == DayOfWeek.SATURDAY || w == DayOfWeek.SUNDAY;
    }

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
