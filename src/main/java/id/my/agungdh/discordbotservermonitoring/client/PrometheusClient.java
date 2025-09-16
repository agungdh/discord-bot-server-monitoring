package id.my.agungdh.discordbotservermonitoring.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class PrometheusClient {

    private final RestClient http;

    public PrometheusClient(
            @Value("${prometheus.baseUrl}") String baseUrl,
            RestClient.Builder builder
    ) {
        this.http = builder.baseUrl(baseUrl).build();
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
            List<Object> value = r.value(); // [ ts, "value" ]
            if (value == null || value.size() < 2) continue;

            double v = Double.parseDouble(String.valueOf(value.get(1)));
            String instance = Objects.toString(metric.getOrDefault("instance",""));
            String alias = Objects.toString(metric.getOrDefault("alias",""));
            out.add(new ResultPoint(instance, alias, v));
        }
        return out;
    }

    /** Range query (matrix), step perMinutes */
    public List<RangePoint> rangeQuery(String promql,
                                       Instant start,
                                       Instant end,
                                       Duration step,
                                       String targetInstance,
                                       String targetAlias) {
        String uri = UriComponentsBuilder.fromPath("/api/v1/query_range")
                .queryParam("query", promql)
                .queryParam("start", start.getEpochSecond())
                .queryParam("end", end.getEpochSecond())
                .queryParam("step", step.getSeconds() + "s")
                .build(true) // jangan escape plus/minus di promql
                .toUriString();

        PrometheusRangeResponse resp = this.http.get()
                .uri(uri)
                .retrieve()
                .body(PrometheusRangeResponse.class);

        if (resp == null || resp.data() == null || resp.data().result() == null) {
            return List.of();
        }

        // cari seri yang match instance+alias
        Optional<PromResultRange> match = resp.data().result().stream()
                .filter(rr -> {
                    String inst = Objects.toString(rr.metric().getOrDefault("instance",""));
                    String alias = Objects.toString(rr.metric().getOrDefault("alias",""));
                    boolean instOk = inst.equals(targetInstance);
                    boolean aliasOk = Objects.toString(targetAlias, "").isBlank()
                            ? alias.isBlank()
                            : alias.equals(targetAlias);
                    return instOk && aliasOk;
                })
                .findFirst();

        if (match.isEmpty()) return List.of();

        // map values -> RangePoint(ts, val)
        return match.get().values().stream()
                .map(pair -> {
                    // pair: [ ts, "value" ]
                    if (pair == null || pair.size() < 2) return null;
                    long epoch = (long) Double.parseDouble(String.valueOf(pair.get(0)));
                    double v = Double.parseDouble(String.valueOf(pair.get(1)));
                    return new RangePoint(Instant.ofEpochSecond(epoch), v);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /** Output sederhana dipakai layer lain */
    public record ResultPoint(String instance, String alias, double value) {}
    public record RangePoint(Instant timestamp, double value) {}

    /** ===== DTO untuk response Prometheus (instant) ===== */
    public record PrometheusQueryResponse(String status, PromData data) {}
    public record PromData(String resultType, List<PromResult> result) {}
    public record PromResult(Map<String,String> metric, List<Object> value) {}

    /** ===== DTO untuk response Prometheus (range/matrix) ===== */
    public record PrometheusRangeResponse(String status, PromDataRange data) {}
    public record PromDataRange(String resultType, List<PromResultRange> result) {}
    public record PromResultRange(Map<String,String> metric, List<List<Object>> values) {}
}
