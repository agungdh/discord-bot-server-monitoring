package id.my.agungdh.discordbotservermonitoring.service;

import id.my.agungdh.discordbotservermonitoring.client.PrometheusClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Mengambil "jumlah menit error" per target (instance/alias) untuk berbagai rentang waktu.
 * Definisi ERROR per menit:
 * - sum_over_time((probe_success == 0)[1m:1s]) >= THRESHOLD_FAILS_PER_MIN
 * - AND count_over_time(probe_success[1m:1s])   >= MIN_SAMPLES_PER_MIN
 * <p>
 * Hasil method = List<ResultPoint> (instance, alias, value) — value = menit error dalam rentang tsb.
 */
@Service
@RequiredArgsConstructor
public class ErrorMinutesService {

    // Ambang gagal/min & minimal sampel/min
    private static final int THRESHOLD_FAILS_PER_MIN = 5;
    private static final int MIN_SAMPLES_PER_MIN = 5;
    // Subquery steps
    private static final String INNER_RESOLUTION = "1s";  // resolusi hitung per detik (sesuai scrape 1s)
    private static final String MINUTE_STEP = "1m";  // agregasi per-menit
    private final PrometheusClient prometheus;
    // ==== Konfigurasi ====
    @Value("${prometheus.blackbox.job:blackbox_ping}")
    private String blackboxJob;

    // ==== Public API ====

    private static ZoneId zone() {
        return ZoneId.systemDefault();
    }

    private static Instant startOfToday() {
        return LocalDate.now(zone()).atStartOfDay(zone()).toInstant();
    }

    private static Instant startOfYesterday() {
        return LocalDate.now(zone()).minusDays(1).atStartOfDay(zone()).toInstant();
    }

    private static Instant startOfNDaysAgo(int days) {
        return LocalDate.now(zone()).minusDays(days).atStartOfDay(zone()).toInstant();
    }

    // (opsional) kalau butuh clamp ke detik
    @SuppressWarnings("unused")
    private static Instant truncateToSecond(Instant t) {
        return t.truncatedTo(ChronoUnit.SECONDS);
    }

    // ==== Core ====

    /**
     * Hari ini: 00:00 lokal → sekarang
     */
    public List<PrometheusClient.ResultPoint> errorMinutesToday() {
        Instant start = startOfToday();
        Instant end = Instant.now();
        return queryErrorMinutes(start, end);
        // nilai value = jumlah menit error (double, tapi isinya integer minutes)
    }

    /**
     * Kemarin penuh: 00:00 → 24:00 (lokal)
     */
    public List<PrometheusClient.ResultPoint> errorMinutesYesterday() {
        Instant start = startOfYesterday();
        Instant end = startOfToday();
        return queryErrorMinutes(start, end);
    }

    // ==== Helpers waktu (pakai zona lokal host) ====

    /**
     * Kemarin lusa penuh: H-2 (00:00) → H-1 (00:00)
     */
    public List<PrometheusClient.ResultPoint> errorMinutesTwoDaysAgo() {
        Instant start = startOfNDaysAgo(2);
        Instant end = startOfNDaysAgo(1);
        return queryErrorMinutes(start, end);
    }

    /**
     * 1 minggu terakhir: H-7 (00:00) → sekarang
     */
    public List<PrometheusClient.ResultPoint> errorMinutesLastWeekUntilNow() {
        Instant start = startOfNDaysAgo(7);
        Instant end = Instant.now();
        return queryErrorMinutes(start, end);
    }

    /**
     * 2 minggu terakhir: H-14 (00:00) → sekarang
     */
    public List<PrometheusClient.ResultPoint> errorMinutesLast2WeeksUntilNow() {
        Instant start = startOfNDaysAgo(14);
        Instant end = Instant.now();
        return queryErrorMinutes(start, end);
    }

    /**
     * Bangun PromQL + panggil /api/v1/query (instant).
     * Kita pakai subquery window [RANGE:1m] @ END, lalu sum by (instance, alias).
     */
    private List<PrometheusClient.ResultPoint> queryErrorMinutes(Instant start, Instant end) {
        long rangeSec = Duration.between(start, end).getSeconds();
        if (rangeSec <= 0) return List.of();

        String promql = buildGuardedMinutesPromql(rangeSec, end.getEpochSecond());
        return prometheus.instantQuery(promql);
    }

    /**
     * Guarded minutes:
     * <p>
     * sum by (instance, alias) (
     * sum_over_time( (
     * (sum_over_time((probe_success{job="..."} == bool 0)[1m:1s]) >= bool THRESH)
     * and
     * (count_over_time(probe_success{job="..."}[1m:1s]) >= bool MIN_SAMPLES)
     * )[${RANGE}s:1m] @ ${END} )
     * )
     */
    private String buildGuardedMinutesPromql(long rangeSec, long endEpoch) {
        String jobLabel = String.format("probe_success{job=\"%s\"}", blackboxJob);

        return """
                sum by (instance, alias) (
                  sum_over_time( (
                    (sum_over_time((%s == bool 0)[1m:%s]) >= bool %d)
                    and
                    (count_over_time(%s[1m:%s]) >= bool %d)
                  )[%ds:%s] @ %d )
                )
                """.formatted(
                jobLabel, INNER_RESOLUTION, THRESHOLD_FAILS_PER_MIN,
                jobLabel, INNER_RESOLUTION, MIN_SAMPLES_PER_MIN,
                rangeSec, MINUTE_STEP, endEpoch
        ).trim();
    }

    public List<PrometheusClient.ResultPoint> errorMinutes(Instant start, Instant end) {
        return queryErrorMinutes(start, end);
    }

    public List<PrometheusClient.ResultPoint> errorMinutesLastHours(int hours) {
        Instant end = Instant.now();
        Instant start = end.minus(Duration.ofHours(hours));
        return queryErrorMinutes(start, end);
    }

    public long errorMinutesAnyDown(Instant start, Instant end) {
        long rangeSec = Duration.between(start, end).getSeconds();
        if (rangeSec <= 0) return 0L;

        // label metric sesuai konfigurasi
        String jobLabel = String.format("probe_success{job=\"%s\"}", blackboxJob);

        // indikator "down per menit per target" (guarded)
        String perMinuteDown = """
                (
                  (sum_over_time((%s == bool 0)[1m:%s]) >= bool %d)
                  and
                  (count_over_time(%s[1m:%s]) >= bool %d)
                )
                """.formatted(
                jobLabel, INNER_RESOLUTION, THRESHOLD_FAILS_PER_MIN,
                jobLabel, INNER_RESOLUTION, MIN_SAMPLES_PER_MIN
        );

        // OR antar target: pakai max by () untuk collapse semua label ⇒ 1 kalau ada target manapun yang down
        String promql = """
                sum_over_time(
                  ( max by () ( %s ) )[%ds:%s] @ %d
                )
                """.formatted(perMinuteDown, rangeSec, MINUTE_STEP, end.getEpochSecond());

        var res = prometheus.instantQuery(promql);
        if (res.isEmpty()) return 0L;
        return Math.round(res.get(0).value());
    }
}
