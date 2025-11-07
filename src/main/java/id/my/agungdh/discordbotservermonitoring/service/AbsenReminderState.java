package id.my.agungdh.discordbotservermonitoring.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class AbsenReminderState {

    private static final Logger log = LoggerFactory.getLogger(AbsenReminderState.class);

    // In-memory toggle
    private volatile boolean enabledToday = true;

    // Catat kapan terakhir auto-reset
    private LocalDate lastReset = LocalDate.now();

    public boolean isEnabledToday() {
        return enabledToday;
    }

    public void setEnabledToday(boolean enabled) {
        this.enabledToday = enabled;
        log.info("[AbsenReminderState] Reminder manually set to {}", enabled ? "ON" : "OFF");
    }

    /**
     * Tiap hari jam 00:00 (WIB) aktifkan lagi otomatis
     */
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Jakarta")
    public void autoEnableEachDay() {
        LocalDate today = LocalDate.now();
        if (!today.equals(lastReset)) {
            enabledToday = true;
            lastReset = today;
            log.info("[AbsenReminderState] Auto-enabled reminder for {}", today);
        }
    }
}
