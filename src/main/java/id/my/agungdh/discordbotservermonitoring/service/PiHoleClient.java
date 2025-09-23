package id.my.agungdh.discordbotservermonitoring.service;

import id.my.agungdh.discordbotservermonitoring.DTO.BlockListResponse;
import id.my.agungdh.discordbotservermonitoring.DTO.SessionWrapper;
import id.my.agungdh.discordbotservermonitoring.DTO.SummaryResponse;
import id.my.agungdh.discordbotservermonitoring.DTO.TopDomainsResponse;
import id.my.agungdh.discordbotservermonitoring.config.PiHoleProperties;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class PiHoleClient {

    private final RestTemplate restTemplate;
    private final PiHoleProperties props;
    private final AtomicReference<SessionRecord> sessionRef = new AtomicReference<>();

    public PiHoleClient(RestTemplate restTemplate, PiHoleProperties props) {
        this.restTemplate = restTemplate;
        this.props = props;
    }

    public SessionWrapper login(String password) {
        String url = props.getBaseUrl() + "/api/auth";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));

        HttpEntity<Map<String, String>> req = new HttpEntity<>(Map.of("password", password), headers);

        ResponseEntity<SessionWrapper> resp =
                restTemplate.exchange(url, HttpMethod.POST, req, SessionWrapper.class);

        if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
            var s = resp.getBody().session();
            if (s != null && s.valid()) {
                Instant expiry = Instant.now().plusSeconds(s.validity());
                sessionRef.set(new SessionRecord(s.sid(), s.csrf(), expiry));
            }
        }
        return resp.getBody();
    }

    public boolean isLoggedIn() {
        SessionRecord rec = sessionRef.get();
        return rec != null && !rec.isExpired();
    }

    public SessionRecord currentSession() {
        return sessionRef.get();
    }

    public TopDomainsResponse getTopDomains(int count) {
        ensureLoggedIn();
        String url = props.getBaseUrl() + "/api/stats/top_domains?count=" + count;

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        headers.add("sid", sessionRef.get().sid());

        return restTemplate.exchange(url, HttpMethod.GET,
                new HttpEntity<>(headers), TopDomainsResponse.class).getBody();
    }

    public TopDomainsResponse getTopBlockedDomains(int count) {
        ensureLoggedIn();
        String url = props.getBaseUrl() + "/api/stats/top_domains?count=" + count + "&blocked=true";

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        headers.add("sid", sessionRef.get().sid());

        return restTemplate.exchange(url, HttpMethod.GET,
                new HttpEntity<>(headers), TopDomainsResponse.class).getBody();
    }

    private void ensureLoggedIn() {
        if (!isLoggedIn()) {
            throw new IllegalStateException("Pi-hole session invalid/expired.");
        }
    }

    public SummaryResponse getSummary() {
        ensureLoggedIn();
        String url = props.getBaseUrl() + "/api/stats/summary";

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        headers.add("sid", sessionRef.get().sid());

        return restTemplate.exchange(url, HttpMethod.GET,
                new HttpEntity<>(headers), SummaryResponse.class).getBody();
    }

    public BlockListResponse getBlockLists() {
        ensureLoggedIn();
        String url = props.getBaseUrl() + "/api/lists/?type=block";

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        headers.add("sid", sessionRef.get().sid());

        return restTemplate.exchange(url, HttpMethod.GET,
                new HttpEntity<>(headers), BlockListResponse.class).getBody();
    }

    public record SessionRecord(String sid, String csrf, Instant expiry) {
        public boolean isExpired() {
            return expiry.isBefore(Instant.now());
        }

        public long secondsRemaining() {
            return Math.max(0, expiry.getEpochSecond() - Instant.now().getEpochSecond());
        }

        public boolean isExpiringSoon(long thresholdSeconds) {
            return secondsRemaining() <= thresholdSeconds;
        }
    }
}
