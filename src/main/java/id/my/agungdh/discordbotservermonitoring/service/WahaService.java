// src/main/java/id/my/agungdh/discordbotservermonitoring/service/WahaService.java
package id.my.agungdh.discordbotservermonitoring.service;

import id.my.agungdh.discordbotservermonitoring.DTO.waha.SendTextResponse;
import id.my.agungdh.discordbotservermonitoring.DTO.waha.WahaSendTextPayload;
import id.my.agungdh.discordbotservermonitoring.util.ChatIdUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

@Service
public class WahaService {

    private final RestClient wahaClient;
    private final String defaultSession;

    public WahaService(RestClient wahaRestClient,
                       @Value("${waha.session:default}") String defaultSession) {
        this.wahaClient = wahaRestClient;
        this.defaultSession = defaultSession;
    }

    public SendTextResponse sendText(String phone, String text) {
        String chatId = ChatIdUtils.toChatId(phone);
        WahaSendTextPayload payload = new WahaSendTextPayload(defaultSession, chatId, text);

        try {
            Map body = wahaClient.post()
                    .uri("/api/sendText")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(Map.class);

            boolean success = body != null && (body.getOrDefault("success", Boolean.TRUE) instanceof Boolean b ? b : true);
            String id = (body != null && body.get("id") != null) ? String.valueOf(body.get("id"))
                    : (body != null && body.get("messageId") != null) ? String.valueOf(body.get("messageId")) : null;

            return new SendTextResponse(success, id, null);

        } catch (RestClientResponseException e) {
            return new SendTextResponse(false, null,
                    "HTTP " + e.getStatusCode().value() + ": " + e.getResponseBodyAsString());
        } catch (RestClientException e) {
            return new SendTextResponse(false, null, e.getMessage());
        }
    }
}
