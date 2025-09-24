// src/main/java/id/my/agungdh/discordbotservermonitoring/DTO/waha/SendTextRequest.java
package id.my.agungdh.discordbotservermonitoring.DTO.waha;

import jakarta.validation.constraints.NotBlank;

public record SendTextRequest(
        @NotBlank String phone,
        @NotBlank String text
) {
}
