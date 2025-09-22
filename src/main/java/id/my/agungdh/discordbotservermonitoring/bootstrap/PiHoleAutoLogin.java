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

@Component
public class PiHoleAutoLogin {
    private static final Logger log = LoggerFactory.getLogger(PiHoleAutoLogin.class);

    private final PiHoleClient client;
    private final PiHoleProperties props;

    // relogin kalau sisa masa berlaku <= 60 detik
    private static final long REL_LOGIN_THRESHOLD_SEC = 60;

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
                log.info("Pi-hole login OK. Expiry: {} (remaining ~{}s)", sess.expiry(), sess.secondsRemaining());
            } else {
                log.warn("Pi-hole login: session null");
            }
        } catch (Exception e) {
            log.error("Pi-hole auto-login gagal: {}", e.getMessage());
        }
    }

    @Scheduled(
            fixedDelayString = "${pihole.relogin-interval-ms:1200000}", // 20 menit
            initialDelayString = "${pihole.relogin-initial-delay-ms:60000}" // TUNDA 60s setelah start
    )
    public void keepAlive() {
        try {
            var sess = client.currentSession();
            if (sess == null || sess.isExpired() || sess.isExpiringSoon(REL_LOGIN_THRESHOLD_SEC)) {
                long rem = (sess == null ? -1 : sess.secondsRemaining());
                log.info("Session {} (remaining {}s). Re-login...",
                        (sess == null ? "null" : (sess.isExpired() ? "expired" : "expiring soon")), rem);

                client.login(props.getPassword());

                var newSess = client.currentSession();
                if (newSess != null) {
                    log.info("Re-login OK. Expiry: {} (remaining ~{}s)", newSess.expiry(), newSess.secondsRemaining());
                } else {
                    log.warn("Re-login done, tapi session null.");
                }
            }
        } catch (Exception e) {
            log.warn("Re-login Pi-hole gagal: {}", e.getMessage());
        }
    }
}
