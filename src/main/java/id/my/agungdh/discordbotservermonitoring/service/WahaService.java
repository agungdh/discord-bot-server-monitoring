// src/main/java/id/my/agungdh/discordbotservermonitoring/service/WahaService.java
package id.my.agungdh.discordbotservermonitoring.service;

import id.my.agungdh.discordbotservermonitoring.DTO.waha.SendTextResponse;
import id.my.agungdh.discordbotservermonitoring.DTO.waha.WahaSendTextPayload;
import id.my.agungdh.discordbotservermonitoring.util.ChatIdUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class WahaService {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String defaultSession;
    private final String apiKey;

    public WahaService(RestTemplate restTemplate,
                       @Value("${waha.base-url}") String baseUrl,
                       @Value("${waha.session:default}") String defaultSession,
                       @Value("${waha.api-key}") String apiKey
    ) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.defaultSession = defaultSession;
        this.apiKey = apiKey;
    }

    public SendTextResponse sendText(String phone, String text) {
        String chatId = ChatIdUtils.toChatId(phone);
        System.out.println("ChatID: " + chatId);
        WahaSendTextPayload payload = new WahaSendTextPayload(defaultSession, chatId, text);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-API-KEY", apiKey); // <— tambahkan di sini
        HttpEntity<WahaSendTextPayload> entity = new HttpEntity<>(payload, headers);

        try {
            ResponseEntity<Map> resp = restTemplate.postForEntity(
                    baseUrl + "/api/sendText", entity, Map.class);

            Map body = resp.getBody();
            boolean success = body != null && (body.getOrDefault("success", Boolean.TRUE) instanceof Boolean b ? b : true);
            String id = (body != null && body.get("id") != null) ? String.valueOf(body.get("id"))
                    : (body != null && body.get("messageId") != null) ? String.valueOf(body.get("messageId")) : null;

            return new SendTextResponse(success, id, null);
        } catch (HttpStatusCodeException e) {
            return new SendTextResponse(false, null,
                    "HTTP " + e.getStatusCode().value() + ": " + e.getResponseBodyAsString());
        } catch (Exception e) {
            return new SendTextResponse(false, null, e.getMessage());
        }
    }
}
