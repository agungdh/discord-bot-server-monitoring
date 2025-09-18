package id.my.agungdh.discordbotservermonitoring.controller;

import id.my.agungdh.discordbotservermonitoring.DTO.monitoring.ErrorMinutesSummaryDTO;
import id.my.agungdh.discordbotservermonitoring.DTO.monitoring.MetricsDTO;
import id.my.agungdh.discordbotservermonitoring.DTO.monitoring.TargetErrorDTO;
import id.my.agungdh.discordbotservermonitoring.client.PrometheusClient;
import id.my.agungdh.discordbotservermonitoring.service.DiscordService;
import id.my.agungdh.discordbotservermonitoring.service.ErrorMinutesService;
import id.my.agungdh.discordbotservermonitoring.service.MetricsService;
import id.my.agungdh.discordbotservermonitoring.time.IntervalService;
import id.my.agungdh.discordbotservermonitoring.time.PeriodDuration;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.*;
import java.util.List;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class MainController {
    private final MetricsService metricsService;
    private final DiscordService discordService;
    private final IntervalService intervalService;
    private final ErrorMinutesService errorMinutesService;   // ⬅️ inject

    @Value("${spring.application.name}")
    String name;

    private static ZoneId zone() {
        return ZoneId.systemDefault();
    }

    private static Instant startOfToday() {
        return LocalDate.now(zone()).atStartOfDay(zone()).toInstant();
    }

    private static Instant startOfYesterday() {
        return LocalDate.now(zone()).minusDays(1).atStartOfDay(zone()).toInstant();
    }

    // ========================= ERRORS (menit error) =========================

    private static Instant startOfNDaysAgo(int days) {
        return LocalDate.now(zone()).minusDays(days).atStartOfDay(zone()).toInstant();
    }

    @GetMapping
    public String index() {
        return name;
    }

    @GetMapping("/system")
    public MetricsDTO system() {
        return metricsService.snapshot(true);
    }

    // Contoh endpoint: hitung mundur interval dari sekarang
    @GetMapping("/interval")
    public String interval(@RequestParam(name = "n") String n) {
        PeriodDuration pd = intervalService.parseInterval(n);

        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime before = pd.subtractFrom(now);

        return "Sekarang: " + now + "\n"
                + "Interval: " + pd.toIsoString() + "\n"
                + "Hasil   : " + before;
    }

    @GetMapping("/errors/today")
    public ErrorMinutesSummaryDTO errorsToday() {
        var points = errorMinutesService.errorMinutesToday();
        var start = startOfToday();
        var end = Instant.now();
        return buildSummary("today", start, end, points);
    }

    @GetMapping("/errors/yesterday")
    public ErrorMinutesSummaryDTO errorsYesterday() {
        var points = errorMinutesService.errorMinutesYesterday();
        var start = startOfYesterday();
        var end = startOfToday();
        return buildSummary("yesterday", start, end, points);
    }

    @GetMapping("/errors/twodaysago")
    public ErrorMinutesSummaryDTO errorsTwoDaysAgo() {
        var points = errorMinutesService.errorMinutesTwoDaysAgo();
        var start = startOfNDaysAgo(2);
        var end = startOfNDaysAgo(1);
        return buildSummary("two-days-ago", start, end, points);
    }

    // ========================= Helpers =========================

    // 1 minggu terakhir: H-7 00:00 → sekarang
    @GetMapping("/errors/last-week")
    public ErrorMinutesSummaryDTO errorsLastWeekUntilNow() {
        var points = errorMinutesService.errorMinutesLastWeekUntilNow();
        var start = startOfNDaysAgo(7);
        var end = Instant.now();
        return buildSummary("last-week-until-now", start, end, points);
    }

    // 2 minggu terakhir: H-14 00:00 → sekarang
    @GetMapping("/errors/last-2weeks")
    public ErrorMinutesSummaryDTO errorsLast2WeeksUntilNow() {
        var points = errorMinutesService.errorMinutesLast2WeeksUntilNow();
        var start = startOfNDaysAgo(14);
        var end = Instant.now();
        return buildSummary("last-2weeks-until-now", start, end, points);
    }

    // Generic: sejak interval dinamis (pakai IntervalService), contoh: /errors/since?n=7d
    // Artinya: start = (now - n) dibulatkan ke 00:00 lokal, end = now
    @GetMapping("/errors/since")
    public ErrorMinutesSummaryDTO errorsSince(@RequestParam(name = "n") String n) {
        PeriodDuration pd = intervalService.parseInterval(n);
        ZonedDateTime nowZ = ZonedDateTime.now();
        ZonedDateTime thenZ = pd.subtractFrom(nowZ).with(LocalTime.MIDNIGHT); // H-N 00:00 lokal
        Instant start = thenZ.toInstant();
        Instant end = nowZ.toInstant();

        // langsung reuse service yang existing
        var points = fetchByAbsoluteRange(start, end);
        return buildSummary("since-" + pd.toIsoString(), start, end, points);
    }

    // Generic absolut: /errors?start=2025-09-01T00:00:00+07:00&end=2025-09-02T00:00:00+07:00
    @GetMapping("/errors")
    public ErrorMinutesSummaryDTO errorsAbsolute(
            @RequestParam("start") String startIso,
            @RequestParam("end") String endIso
    ) {
        Instant start = ZonedDateTime.parse(startIso).toInstant();
        Instant end = ZonedDateTime.parse(endIso).toInstant();
        var points = fetchByAbsoluteRange(start, end);
        return buildSummary("custom", start, end, points);
    }

    private ErrorMinutesSummaryDTO buildSummary(String period, Instant start, Instant end,
                                                List<PrometheusClient.ResultPoint> points) {
        long total = Math.round(points.stream().mapToDouble(PrometheusClient.ResultPoint::value).sum());
        var list = points.stream()
                .map(p -> TargetErrorDTO.builder()
                        .instance(p.instance())
                        .alias(p.alias())
                        .minutes(Math.round(p.value()))
                        .build())
                .toList();

        return ErrorMinutesSummaryDTO.builder()
                .period(period)
                .start(start)
                .end(end)
                .totalMinutes(total)
                .results(list)
                .build();
    }

    private List<PrometheusClient.ResultPoint> fetchByAbsoluteRange(Instant start, Instant end) {
        // Pakai metode private dari service yang existing via wrapper “sementara”.
        // Atau kamu bisa expose method publik di ErrorMinutesService untuk range arbitrary.
        // Di sini aku panggil langsung promQL builder yang sama lewat helper kecil:
        return errorMinutesServiceErrorRange(start, end);
    }

    // --- Wrapper kecil karena ErrorMinutesService tidak expose method "by range" publik ---
    private List<PrometheusClient.ResultPoint> errorMinutesServiceErrorRange(Instant start, Instant end) {
        // Trik sederhana: tambahkan method public di ErrorMinutesService kalau mau lebih bersih.
        try {
            var m = ErrorMinutesService.class.getDeclaredMethod("queryErrorMinutes", Instant.class, Instant.class);
            m.setAccessible(true);
            //noinspection unchecked
            return (List<PrometheusClient.ResultPoint>) m.invoke(errorMinutesService, start, end);
        } catch (Exception e) {
            throw new RuntimeException("Failed to query error minutes by range", e);
        }
    }
}
