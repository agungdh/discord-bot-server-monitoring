// config/AsyncConfig.java
package id.my.agungdh.discordbotservermonitoring.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "commandExecutor")
    public Executor commandExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(Math.max(4, Runtime.getRuntime().availableProcessors()));
        ex.setMaxPoolSize(16);
        ex.setQueueCapacity(200);
        ex.setThreadNamePrefix("discord-cmd-");
        ex.initialize();
        return ex;
    }
}
