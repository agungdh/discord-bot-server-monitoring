package id.my.agungdh.discordbotservermonitoring.DTO.monitoring;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErrorMinutesSummaryDTO {
    private String period;          // mis: "today", "yesterday"
    private Instant start;          // epoch start (UTC)
    private Instant end;            // epoch end (UTC / now)
    private long totalMinutes;      // total semua target
    private List<TargetErrorDTO> results; // per target
}

