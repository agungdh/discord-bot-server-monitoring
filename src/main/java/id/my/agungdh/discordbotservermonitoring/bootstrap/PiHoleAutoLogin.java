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
                log.info("Pi-hole login OK. Expiry: {}", sess.expiry());
            }
        } catch (Exception e) {
            log.error("Pi-hole auto-login gagal: {}", e.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${pihole.relogin-interval-ms:1500000}")
    public void keepAlive() {
        if (!client.isLoggedIn()) {
            log.info("Session expired. Re-login...");
            client.login(props.getPassword());
        }
    }
}
