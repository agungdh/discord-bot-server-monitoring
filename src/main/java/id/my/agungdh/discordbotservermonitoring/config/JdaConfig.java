package id.my.agungdh.discordbotservermonitoring.config;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JdaConfig {
    @Bean
    public JDA jda(@Value("${discord.token}") String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("DISCORD_TOKEN belum diset");
        }
        // Interactions (slash) bisa jalan bahkan tanpa intents khusus
        // (gunakan createLight dan tanpa intents untuk minimalis).
        return JDABuilder.createLight(token).build();
    }
}
