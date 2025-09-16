package id.my.agungdh.discordbotservermonitoring.controller;

import id.my.agungdh.discordbotservermonitoring.DTO.monitoring.MetricsDTO;
import id.my.agungdh.discordbotservermonitoring.service.DiscordService;
import id.my.agungdh.discordbotservermonitoring.service.MetricsService;
import lombok.RequiredArgsConstructor;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.data.category.DefaultCategoryDataset;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class MainController {
    private final MetricsService metricsService;
    private final DiscordService discordService;
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

    @PostMapping("/send")
    public ResponseEntity<String> sendMessage(@RequestParam String guildId,
                                              @RequestParam String channelId,
                                              @RequestBody String message) {
        discordService.sendMessage(guildId, channelId, message);
        return ResponseEntity.ok("Message sent");
    }

    @GetMapping("/chart")
    public void testChart() throws IOException {
        // Create a dataset
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        dataset.addValue(10, "Sales", "January");
        dataset.addValue(15, "Sales", "February");
        dataset.addValue(20, "Sales", "March");

        // Create a chart
        JFreeChart barChart = ChartFactory.createBarChart(
                "Monthly Sales",    // Chart title
                "Month",            // Category axis label
                "Sales",            // Value axis label
                dataset
        );

        // Buat file di temporary directory
        File tempFile = File.createTempFile("SalesChart_", ".png");

        // Simpan chart ke file PNG
        ChartUtils.saveChartAsPNG(tempFile, barChart, 640, 480);

        System.out.println("Chart disimpan di: " + tempFile.getAbsolutePath());
    }
}
