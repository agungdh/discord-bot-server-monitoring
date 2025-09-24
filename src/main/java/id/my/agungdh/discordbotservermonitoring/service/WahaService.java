// src/main/java/id/my/agungdh/discordbotservermonitoring/service/WahaService.java
package id.my.agungdh.discordbotservermonitoring.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import id.my.agungdh.discordbotservermonitoring.DTO.waha.SendTextResponse;
import id.my.agungdh.discordbotservermonitoring.DTO.waha.WahaSendTextPayload;
import id.my.agungdh.discordbotservermonitoring.util.ChatIdUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Service
public class WahaService {

    private final HttpClient httpClient;
    private final ObjectMapper om = new ObjectMapper();
    private final String baseUrl;
    private final String defaultSession;
    private final String apiKey;

    public WahaService(@Value("${waha.base-url}") String baseUrl,
                       @Value("${waha.session:default}") String defaultSession,
                       @Value("${waha.api-key}") String apiKey) {
        this.baseUrl = baseUrl;
        this.defaultSession = defaultSession;
        this.apiKey = apiKey;

        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public SendTextResponse sendText(String phone, String text) {
        try {
            String chatId = ChatIdUtils.toChatId(phone);
            WahaSendTextPayload payload = new WahaSendTextPayload(defaultSession, chatId, text);
            String json = om.writeValueAsString(payload);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/sendText"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("X-API-KEY", apiKey)
                    .header("User-Agent", "curl/8.5.0")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                Map<String, Object> body = resp.body() != null && !resp.body().isBlank()
                        ? om.readValue(resp.body(), Map.class)
                        : Map.of();

                Object successObj = body.getOrDefault("success", Boolean.TRUE);
                boolean success = (successObj instanceof Boolean) ? (Boolean) successObj : true;

                String id = body.containsKey("id") ? String.valueOf(body.get("id"))
                        : (body.containsKey("messageId") ? String.valueOf(body.get("messageId")) : null);

                return new SendTextResponse(success, id, null);
            } else {
                return new SendTextResponse(false, null,
                        "HTTP " + resp.statusCode() + ": " + resp.body());
            }
        } catch (Exception e) {
            return new SendTextResponse(false, null, e.getMessage());
        }
    }
}
