package id.my.agungdh.discordbotservermonitoring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DiscordBotServerMonitoringApplication {

    public static void main(String[] args) {
        SpringApplication.run(DiscordBotServerMonitoringApplication.class, args);
    }

}
