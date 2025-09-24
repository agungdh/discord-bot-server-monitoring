// src/main/java/id/my/agungdh/discordbotservermonitoring/config/RestClientConfig.java
package id.my.agungdh.discordbotservermonitoring.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient wahaRestClient(RestClient.Builder builder,
                                     @Value("${waha.base-url}") String baseUrl,
                                     @Value("${waha.api-key}") String apiKey) {
        return builder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .defaultHeader("X-API-KEY", apiKey)
                .build();
    }
}
