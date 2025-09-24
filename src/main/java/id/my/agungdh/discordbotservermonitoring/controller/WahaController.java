// src/main/java/id/my/agungdh/discordbotservermonitoring/controller/WahaController.java
package id.my.agungdh.discordbotservermonitoring.controller;

import id.my.agungdh.discordbotservermonitoring.DTO.waha.SendTextRequest;
import id.my.agungdh.discordbotservermonitoring.DTO.waha.SendTextResponse;
import id.my.agungdh.discordbotservermonitoring.service.WahaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/whatsapp")
public class WahaController {

    private final WahaService wahaService;

    public WahaController(WahaService wahaService) {
        this.wahaService = wahaService;
    }

    @PostMapping("/send-text")
    public ResponseEntity<SendTextResponse> sendText(@Valid @RequestBody SendTextRequest req) {
        SendTextResponse res = wahaService.sendText(req.phone(), req.text());
        return res.success() ? ResponseEntity.ok(res) : ResponseEntity.badRequest().body(res);
    }
}
