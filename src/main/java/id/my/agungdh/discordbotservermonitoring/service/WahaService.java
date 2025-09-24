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

    /**
     * Kirim teks dengan emulasi typing:
     * startTyping -> (delay) -> stopTyping -> sendText
     */
    public SendTextResponse sendText(String phone, String text) {
        try {
            String chatId = ChatIdUtils.toChatId(phone);

            // 1) Start typing
            callTyping("/api/startTyping", chatId);

            // 2) Delay "ngetik" yang realistis (±50ms/karakter, min 800ms, max 3500ms)
            long typingDelayMs = Math.max(800, Math.min(3500, (text != null ? text.length() : 0) * 50L));
            try { Thread.sleep(typingDelayMs); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }

            // 3) Stop typing (best-effort)
            callTyping("/api/stopTyping", chatId);

            // 4) Kirim pesan
            WahaSendTextPayload payload = new WahaSendTextPayload(defaultSession, chatId, text);
            Map<String, Object> body = postJson("/api/sendText", payload);

            Object successObj = body.getOrDefault("success", Boolean.TRUE);
            boolean success = (successObj instanceof Boolean) ? (Boolean) successObj : true;

            String id = body.containsKey("id") ? String.valueOf(body.get("id"))
                    : (body.containsKey("messageId") ? String.valueOf(body.get("messageId")) : null);

            return new SendTextResponse(success, id, null);

        } catch (Exception e) {
            return new SendTextResponse(false, null, e.getMessage());
        }
    }

    /* ====================== Helpers ====================== */

    private void callTyping(String path, String chatId) {
        try {
            WahaSendTextPayload payload = new WahaSendTextPayload(defaultSession, chatId, null);
            postJson(path, payload);
        } catch (Exception ex) {
            // best-effort; jangan gagalkan seluruh flow hanya karena typing gagal
            System.err.println("Typing API error (" + path + "): " + ex.getMessage());
        }
    }

    private Map<String, Object> postJson(String path, Object bodyObj) throws Exception {
        String json = om.writeValueAsString(bodyObj);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("X-API-KEY", apiKey)
                .header("User-Agent", "curl/8.5.0")
                .POST(HttpRequest.BodyPublishers.ofString(json)) // Content-Length, bukan chunked
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

        if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
            String body = resp.body();
            if (body == null || body.isBlank()) return Map.of();
            //noinspection unchecked
            return om.readValue(body, Map.class);
        } else {
            throw new RuntimeException("HTTP " + resp.statusCode() + ": " + resp.body());
        }
    }
}
