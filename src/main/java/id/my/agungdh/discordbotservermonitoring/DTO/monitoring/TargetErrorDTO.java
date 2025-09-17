package id.my.agungdh.discordbotservermonitoring.DTO.monitoring;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TargetErrorDTO {
    private String instance;
    private String alias;
    private long minutes;           // jumlah menit error untuk target tsb
}
