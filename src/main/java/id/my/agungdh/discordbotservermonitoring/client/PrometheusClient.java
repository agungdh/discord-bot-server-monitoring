package id.my.agungdh.discordbotservermonitoring.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Prometheus client sederhana untuk instant query dan range query.
 * Catatan: rangeQuery memakai POST application/x-www-form-urlencoded
 * supaya aman untuk promql multiline / mengandung spasi & newline.
 */
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

    /**
     * Instant query (vector) — sudah dari kamu
     */
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
            String instance = Objects.toString(metric.getOrDefault("instance", ""));
            String alias = Objects.toString(metric.getOrDefault("alias", ""));
            out.add(new ResultPoint(instance, alias, v));
        }
        return out;
    }

    /**
     * Range query (matrix) — PAKAI POST FORM-URLENCODED (bukan GET)
     */
    public List<RangePoint> rangeQuery(String promql,
                                       Instant start,
                                       Instant end,
                                       Duration step,
                                       String targetInstance,
                                       String targetAlias) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("query", promql);
        form.add("start", Long.toString(start.getEpochSecond()));
        form.add("end", Long.toString(end.getEpochSecond()));
        form.add("step", step.toSeconds() + "s"); // contoh: "60s"

        PrometheusRangeResponse resp = this.http.post()
                .uri("/api/v1/query_range")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(PrometheusRangeResponse.class);

        if (resp == null || resp.data() == null || resp.data().result() == null) {
            return List.of();
        }

        // pilih time series yang match persis instance + alias
        Optional<PromResultRange> match = resp.data().result().stream()
                .filter(rr -> {
                    String inst = Objects.toString(rr.metric().getOrDefault("instance", ""));
                    String als = Objects.toString(rr.metric().getOrDefault("alias", ""));
                    return inst.equals(targetInstance) &&
                            als.equals(Objects.toString(targetAlias, ""));
                })
                .findFirst();

        if (match.isEmpty()) return List.of();

        List<RangePoint> out = new ArrayList<>();
        for (List<Object> pair : match.get().values()) {
            // pair: [ ts, "value" ]
            if (pair == null || pair.size() < 2) continue;
            long epoch = (long) Double.parseDouble(String.valueOf(pair.get(0)));
            double v = Double.parseDouble(String.valueOf(pair.get(1)));
            out.add(new RangePoint(Instant.ofEpochSecond(epoch), v));
        }
        return out;
    }

    /**
     * Output sederhana yang dipakai di layer lain
     */
    public record ResultPoint(String instance, String alias, double value) {
    }

    public record RangePoint(Instant timestamp, double value) {
    }

    /**
     * ===== DTO untuk response Prometheus (instant) =====
     */
    public record PrometheusQueryResponse(String status, PromData data) {
    }

    public record PromData(String resultType, List<PromResult> result) {
    }

    public record PromResult(Map<String, String> metric, List<Object> value) {
    }

    /**
     * ===== DTO untuk response Prometheus (range/matrix) =====
     */
    public record PrometheusRangeResponse(String status, PromDataRange data) {
    }

    public record PromDataRange(String resultType, List<PromResultRange> result) {
    }

    public record PromResultRange(Map<String, String> metric, List<List<Object>> values) {
    }
}
