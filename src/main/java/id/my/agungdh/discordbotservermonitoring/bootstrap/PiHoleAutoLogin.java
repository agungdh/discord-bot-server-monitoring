package id.my.agungdh.discordbotservermonitoring.bootstrap;

import id.my.agungdh.discordbotservermonitoring.config.PiHoleProperties;
import id.my.agungdh.discordbotservermonitoring.service.PiHoleClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ScheduledFuture;

@Component
public class PiHoleAutoLogin {
    private static final Logger log = LoggerFactory.getLogger(PiHoleAutoLogin.class);
    // relogin H-5 menit (ubah sesuka hati)
    private static final long REL_LOGIN_THRESHOLD_SEC = 300;
    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z (O)");
    private final PiHoleClient client;
    private final PiHoleProperties props;
    private final TaskScheduler scheduler;
    private volatile ScheduledFuture<?> nextRefreshTask;

    public PiHoleAutoLogin(PiHoleClient client, PiHoleProperties props, TaskScheduler scheduler) {
        this.client = client;
        this.props = props;
        this.scheduler = scheduler;
    }

    private static String fmt(Instant instantUtc) {
        return TS_FMT.format(instantUtc.atZone(ZONE));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loginOnStartup() {
        try {
            client.login(props.getPassword());
            var sess = client.currentSession();
            if (sess != null) {
                log.info("Pi-hole login OK. Expiry: {} (remaining ~{}s)",
                        fmt(sess.expiry()), sess.secondsRemaining());
                scheduleNextRefresh();
            } else {
                log.warn("Pi-hole login: session null");
            }
        } catch (Exception e) {
            log.error("Pi-hole auto-login gagal: {}", e.getMessage());
            // fallback: coba lagi 1 menit kemudian
            scheduleRetry(Duration.ofMinutes(1));
        }
    }

    private void scheduleNextRefresh() {
        var sess = client.currentSession();
        if (sess == null) return;

        Instant when = sess.expiry().minusSeconds(REL_LOGIN_THRESHOLD_SEC);
        Instant now = Instant.now();

        if (when.isBefore(now)) {
            // kalau udah mepet/terlewat, relogin segera
            log.info("Expiry-{}s sudah terlewat, relogin segera.", REL_LOGIN_THRESHOLD_SEC);
            doReloginAndReschedule();
            return;
        }

        // cancel jadwal lama (kalau ada)
        var old = nextRefreshTask;
        if (old != null) old.cancel(false);

        long secs = Duration.between(now, when).getSeconds();
        log.info("Menjadwalkan relogin pada {} ({}s dari sekarang).", fmt(when), secs);

        nextRefreshTask = scheduler.schedule(this::doReloginAndReschedule, when);
    }

    private void doReloginAndReschedule() {
        try {
            var before = client.currentSession();
            String beforeStr = (before == null ? "-" : fmt(before.expiry()));
            log.info("Relogin trigger (current expiry: {}).", beforeStr);

            client.login(props.getPassword());

            var newSess = client.currentSession();
            if (newSess != null) {
                log.info("Re-login OK. Expiry: {} (remaining ~{}s)",
                        fmt(newSess.expiry()), newSess.secondsRemaining());
                scheduleNextRefresh();
            } else {
                log.warn("Re-login done, tapi session null. Coba lagi 1 menit.");
                scheduleRetry(Duration.ofMinutes(1));
            }
        } catch (Exception e) {
            log.warn("Relogin gagal: {}. Coba lagi 1 menit.", e.getMessage());
            scheduleRetry(Duration.ofMinutes(1));
        }
    }

    private void scheduleRetry(Duration delay) {
        var old = nextRefreshTask;
        if (old != null) old.cancel(false);

        Instant when = Instant.now().plus(delay);
        log.info("Menjadwalkan retry relogin pada {} (+{}s).", fmt(when), delay.getSeconds());
        nextRefreshTask = scheduler.schedule(this::doReloginAndReschedule, when);
    }
}
