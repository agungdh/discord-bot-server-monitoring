package id.my.agungdh.discordbotservermonitoring.config;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JdaConfig {
    @Bean
    public JDA jda(@Value("${discord.token}") String token) throws InterruptedException {
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("DISCORD_TOKEN belum diset");
        }
        JDA jda = JDABuilder.createLight(token).build();
        jda.awaitReady(); // <— blok sampai ReadyEvent, cache guild/channel sudah siap
        return jda;
    }
}
