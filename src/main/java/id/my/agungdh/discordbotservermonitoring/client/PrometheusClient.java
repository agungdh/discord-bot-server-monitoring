package id.my.agungdh.discordbotservermonitoring.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class PrometheusClient {

    private final RestClient http;

    public PrometheusClient(
            @Value("${prometheus.baseUrl}") String baseUrl,
            RestClient.Builder builder
    ) {
        // baseUrl contoh: http://localhost:9090
        this.http = builder
                .baseUrl(baseUrl)
                .build();
    }

    /** Instant query (vector) */
    public List<ResultPoint> instantQuery(String promql) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("query", promql);

        PrometheusQueryResponse resp = this.http.post()
                .uri("/api/v1/query")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(PrometheusQueryResponse.class);

        if (resp == null || resp.data() == null || resp.data().result() == null) {
            return List.of();
        }

        List<ResultPoint> out = new ArrayList<>();
        for (PromResult r : resp.data().result()) {
            Map<String, String> metric = r.metric();
            List<Object> value = r.value(); // [ ts(double/string), "value(string)" ]
            if (value == null || value.size() < 2) continue;

            double v = Double.parseDouble(String.valueOf(value.get(1)));
            String instance = Objects.toString(metric.getOrDefault("instance",""));
            String alias = Objects.toString(metric.getOrDefault("alias",""));
            out.add(new ResultPoint(instance, alias, v));
        }
        return out;
    }

    /** Output sederhana yang kamu pakai di layer lain */
    public record ResultPoint(String instance, String alias, double value) {}

    /** ===== DTO untuk response Prometheus ===== */
    public record PrometheusQueryResponse(String status, PromData data) {}
    public record PromData(String resultType, List<PromResult> result) {}
    public record PromResult(Map<String,String> metric, List<Object> value) {}
}
