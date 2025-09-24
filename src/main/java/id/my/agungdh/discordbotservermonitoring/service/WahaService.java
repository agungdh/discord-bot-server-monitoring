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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

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
     * VERSI NON-BLOCKING:
     * startTyping -> (delay 500–1500ms non-blocking) -> stopTyping -> sendText
     */
    public CompletableFuture<SendTextResponse> sendTextAsync(String phone, String text) {
        String chatId = ChatIdUtils.toChatId(phone);
        long typingDelayMs = 500 + (long) (Math.random() * 1000);

        // 1) startTyping (async, best-effort)
        CompletableFuture<Void> start = callTypingAsync("/api/startTyping", chatId)
                .exceptionally(ex -> {
                    System.err.println("Typing API error (/startTyping): " + ex.getMessage());
                    return null;
                });

        // 2) setelah delay non-blocking, stopTyping
        CompletableFuture<Void> stop = start.thenCompose(v ->
                CompletableFuture.runAsync(() -> {
                        }, CompletableFuture.delayedExecutor(typingDelayMs, TimeUnit.MILLISECONDS))
                        .thenCompose(v2 -> callTypingAsync("/api/stopTyping", chatId))
                        .exceptionally(ex -> {
                            System.err.println("Typing API error (/stopTyping): " + ex.getMessage());
                            return null;
                        })
        );

        // 3) kirim pesan setelah stopTyping (atau walau stopTyping gagal)
        return stop.thenCompose(v -> {
            WahaSendTextPayload payload = new WahaSendTextPayload(defaultSession, chatId, text);
            return postJsonAsync("/api/sendText", payload)
                    .thenApply(this::mapToSendTextResponse);
        }).exceptionally(ex ->
                new SendTextResponse(false, null, ex.getMessage())
        );
    }

    /**
     * VERSI SINKRON (tetap disediakan untuk kompatibilitas controller lama).
     * Ini akan mem-BLOCK thread pemanggil (gunakan hanya kalau memang perlu).
     */
    public SendTextResponse sendText(String phone, String text) {
        try {
            return sendTextAsync(phone, text).get(); // blocking
        } catch (Exception e) {
            return new SendTextResponse(false, null, e.getMessage());
        }
    }

    /* ====================== Helpers (async) ====================== */

    private CompletableFuture<Void> callTypingAsync(String path, String chatId) {
        WahaSendTextPayload payload = new WahaSendTextPayload(defaultSession, chatId, null);
        return postJsonAsync(path, payload).thenApply(m -> null);
    }

    private CompletableFuture<Map<String, Object>> postJsonAsync(String path, Object bodyObj) {
        try {
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

            return httpClient.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                    .thenApply(resp -> {
                        if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                            String body = resp.body();
                            try {
                                if (body == null || body.isBlank()) return Map.of();
                                //noinspection unchecked
                                return om.readValue(body, Map.class);
                            } catch (Exception e) {
                                throw new RuntimeException("Parse response error: " + e.getMessage());
                            }
                        } else {
                            throw new RuntimeException("HTTP " + resp.statusCode() + ": " + resp.body());
                        }
                    });

        } catch (Exception e) {
            CompletableFuture<Map<String, Object>> cf = new CompletableFuture<>();
            cf.completeExceptionally(e);
            return cf;
        }
    }

    private SendTextResponse mapToSendTextResponse(Map<String, Object> body) {
        Object successObj = body.getOrDefault("success", Boolean.TRUE);
        boolean success = (successObj instanceof Boolean) ? (Boolean) successObj : true;

        String id = body.containsKey("id") ? String.valueOf(body.get("id"))
                : (body.containsKey("messageId") ? String.valueOf(body.get("messageId")) : null);

        return new SendTextResponse(success, id, null);
    }
}
