package ma.enset.backend.dto;

import lombok.*;
import ma.enset.backend.entity.NciHistory;

import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class NciHistoryDTO {

        private Long id;
        private Long companyId;
        private String ticker;
        private Float nciValue;
        private LocalDateTime recordedAt;
        private String reason;

        public static NciHistoryDTO from(NciHistory h) {
            return NciHistoryDTO.builder()
                    .id(h.getId())
                    .companyId(h.getCompany().getId())
                    .ticker(h.getCompany().getTicker())
                    .nciValue(h.getNciValue())
                    .recordedAt(h.getRecordedAt())
                    .reason(h.getReason())
                    .build();
        }
    }

