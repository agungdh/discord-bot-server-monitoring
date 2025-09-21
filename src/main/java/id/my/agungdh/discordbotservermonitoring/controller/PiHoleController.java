package id.my.agungdh.discordbotservermonitoring.controller;

import id.my.agungdh.discordbotservermonitoring.DTO.SessionWrapper;
import id.my.agungdh.discordbotservermonitoring.DTO.SummaryResponse;
import id.my.agungdh.discordbotservermonitoring.DTO.TopDomainsResponse;
import id.my.agungdh.discordbotservermonitoring.service.PiHoleClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/pihole")
public class PiHoleController {

    private final PiHoleClient client;

    public PiHoleController(PiHoleClient client) {
        this.client = client;
    }

    @PostMapping("/login")
    public ResponseEntity<SessionWrapper> login(@RequestBody PasswordReq req) {
        return ResponseEntity.ok(client.login(req.password()));
    }

    @GetMapping("/top-domains")
    public ResponseEntity<TopDomainsResponse> topDomains(@RequestParam(defaultValue = "10") int count) {
        return ResponseEntity.ok(client.getTopDomains(count));
    }

    @GetMapping("/top-blocked-domains")
    public ResponseEntity<TopDomainsResponse> topBlocked(@RequestParam(defaultValue = "10") int count) {
        return ResponseEntity.ok(client.getTopBlockedDomains(count));
    }

    @GetMapping("/summary")
    public ResponseEntity<SummaryResponse> summary() {
        return ResponseEntity.ok(client.getSummary());
    }

    public record PasswordReq(String password) {
    }
}
