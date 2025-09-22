// file: id/my/agungdh/discordbotservermonitoring/bootstrap/PiHoleAutoLogin.java
package id.my.agungdh.discordbotservermonitoring.bootstrap;

import id.my.agungdh.discordbotservermonitoring.config.PiHoleProperties;
import id.my.agungdh.discordbotservermonitoring.service.PiHoleClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class PiHoleAutoLogin {
    private static final Logger log = LoggerFactory.getLogger(PiHoleAutoLogin.class);

    private final PiHoleClient client;
    private final PiHoleProperties props;

    // relogin kalau sisa masa berlaku <= 60 detik
    private static final long REL_LOGIN_THRESHOLD_SEC = 60;

    // === gunakan timezone default Spring (sudah di-set saat startup) ===
    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z (O)"); // contoh: 2025-09-22 11:03:06 WIB (+07:00)

    public PiHoleAutoLogin(PiHoleClient client, PiHoleProperties props) {
        this.client = client;
        this.props = props;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loginOnStartup() {
        try {
            client.login(props.getPassword());
            var sess = client.currentSession();
            if (sess != null) {
                log.info("Pi-hole login OK. Expiry: {} (remaining ~{}s)",
                        fmt(sess.expiry()), sess.secondsRemaining());
            } else {
                log.warn("Pi-hole login: session null");
            }
        } catch (Exception e) {
            log.error("Pi-hole auto-login gagal: {}", e.getMessage());
        }
    }

    @Scheduled(
            fixedDelayString = "${pihole.relogin-interval-ms:1200000}", // 20 menit
            initialDelayString = "${pihole.relogin-initial-delay-ms:60000}" // Tunda 60s setelah start
    )
    public void keepAlive() {
        try {
            var sess = client.currentSession();
            if (sess == null || sess.isExpired() || sess.isExpiringSoon(REL_LOGIN_THRESHOLD_SEC)) {
                long rem = (sess == null ? -1 : sess.secondsRemaining());
                String state = (sess == null ? "null" : (sess.isExpired() ? "expired" : "expiring soon"));
                String expStr = (sess == null ? "-" : fmt(sess.expiry()));
                log.info("Session {} (expiry {}, remaining {}s). Re-login...", state, expStr, rem);

                client.login(props.getPassword());

                var newSess = client.currentSession();
                if (newSess != null) {
                    log.info("Re-login OK. Expiry: {} (remaining ~{}s)",
                            fmt(newSess.expiry()), newSess.secondsRemaining());
                } else {
                    log.warn("Re-login done, tapi session null.");
                }
            }
        } catch (Exception e) {
            // jangan biarkan exception mematikan scheduler
            log.warn("Re-login Pi-hole gagal: {}", e.getMessage());
        }
    }

    /** Format Instant → string lokal sesuai timezone Spring (systemDefault). */
    private static String fmt(Instant instantUtc) {
        ZonedDateTime zdt = instantUtc.atZone(ZONE);
        return TS_FMT.format(zdt);
    }
}
