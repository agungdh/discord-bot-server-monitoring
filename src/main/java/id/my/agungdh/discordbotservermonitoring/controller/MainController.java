package id.my.agungdh.discordbotservermonitoring.controller;

import id.my.agungdh.discordbotservermonitoring.DTO.monitoring.MetricsDTO;
import id.my.agungdh.discordbotservermonitoring.service.DiscordService;
import id.my.agungdh.discordbotservermonitoring.service.MetricsService;
import id.my.agungdh.discordbotservermonitoring.time.IntervalService;
import id.my.agungdh.discordbotservermonitoring.time.PeriodDuration;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.time.ZonedDateTime;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class MainController {
    private final MetricsService metricsService;
    private final DiscordService discordService;
    private final IntervalService intervalService; // ⬅️ inject service baru

    @Value("${spring.application.name}")
    String name;

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
}
