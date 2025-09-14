package id.my.agungdh.discordbotservermonitoring.DTO.monitoring;

public record MemoryDTO(
        long totalBytes,
        long usedBytes,
        long freeBytes,
        double usedPercent
) {}
