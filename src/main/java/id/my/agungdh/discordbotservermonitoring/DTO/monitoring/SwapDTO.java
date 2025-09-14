package id.my.agungdh.discordbotservermonitoring.DTO.monitoring;

public record SwapDTO(
        long totalBytes,
        long usedBytes,
        double usedPercent
) {}
