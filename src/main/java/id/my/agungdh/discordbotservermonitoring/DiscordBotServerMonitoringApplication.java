package id.my.agungdh.discordbotservermonitoring;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
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

    @Bean
    RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(5))   // versi baru, non-deprecated
                .readTimeout(Duration.ofSeconds(10))
                .build();
    }
}
