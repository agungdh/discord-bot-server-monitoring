package id.my.agungdh.discordbotservermonitoring;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
public class DiscordBotServerMonitoringApplication {

    public static void main(String[] args) {
        SpringApplication.run(DiscordBotServerMonitoringApplication.class, args);
    }

    @PostConstruct
    public void init() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Jakarta"));
        System.out.println("Default timezone set to " + TimeZone.getDefault().getID());
    }
}
