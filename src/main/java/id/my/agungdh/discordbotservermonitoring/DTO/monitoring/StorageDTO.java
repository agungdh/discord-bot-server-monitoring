package id.my.agungdh.discordbotservermonitoring.DTO.monitoring;

public record StorageDTO(
        String name,
        String type,
        long totalBytes,
        long usableBytes,
        double usedPercent
) {
}
