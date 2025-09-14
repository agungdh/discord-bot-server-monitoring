package id.my.agungdh.discordbotservermonitoring.DTO.monitoring;

import java.time.Instant;
import java.util.List;

public record MetricsDTO(
        Instant timestamp,
        String hostname,
        String os,
        long uptimeSeconds,
        CpuDTO cpu,
        MemoryDTO memory,
        SwapDTO swap,
        List<StorageDTO> storage,
        List<NetworkDTO> networks
) {
}
