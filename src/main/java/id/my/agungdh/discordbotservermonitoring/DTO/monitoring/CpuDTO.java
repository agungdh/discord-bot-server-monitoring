package id.my.agungdh.discordbotservermonitoring.DTO.monitoring;

import java.util.List;

public record CpuDTO(
        String model,
        int physicalCores,
        int logicalCores,
        double systemLoad1m,
        double cpuUsage,
        List<Double> perCoreUsage,
        Double temperatureC
) {}
