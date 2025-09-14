package id.my.agungdh.discordbotservermonitoring.DTO.monitoring;

public record NetworkDTO(
        String name,
        String mac,
        String ipv4,
        String ipv6,
        long bytesRecv,
        long bytesSent
) {}
