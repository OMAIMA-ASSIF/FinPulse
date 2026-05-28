package ma.enset.backend.dto;

import lombok.*;
import ma.enset.backend.entity.Alert;
import ma.enset.backend.enums.AlertType;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertDTO {
    private Long id;
    private Long strategyId;
    private String companyTicker;
    private String companyName;
    private AlertType alertType;
    private String message;
    private Boolean isRead;
    private LocalDateTime createdAt;

    public static AlertDTO from(Alert a) {
        return AlertDTO.builder()
                .id(a.getId())
                .strategyId(a.getUserStrategy().getId())
                .companyTicker(a.getUserStrategy().getCompany().getTicker())
                .companyName(a.getUserStrategy().getCompany().getName())
                .alertType(a.getAlertType())
                .message(a.getMessage())
                .isRead(a.getIsRead())
                .createdAt(a.getCreatedAt())
                .build();
    }
}
