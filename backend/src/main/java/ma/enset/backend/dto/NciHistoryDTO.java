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
            Long companyId = h.getCompany().getIngestionCompanyId() != null
                    ? h.getCompany().getIngestionCompanyId() : h.getCompany().getId();
            return NciHistoryDTO.builder()
                    .id(h.getId())
                    .companyId(companyId)
                    .ticker(h.getCompany().getTicker())
                    .nciValue(h.getNciValue())
                    .recordedAt(h.getRecordedAt())
                    .reason(h.getReason())
                    .build();
        }

        public static NciHistoryDTO fromP1(Long companyId, String ticker, ma.enset.backend.p1.P1SignalHistoryPoint p) {
            return NciHistoryDTO.builder()
                    .id(p.getFilingId())
                    .companyId(companyId)
                    .ticker(ticker)
                    .nciValue(p.getSignalValue() != null ? p.getSignalValue().floatValue() : null)
                    .recordedAt(p.getComputedAt() != null ? p.getComputedAt().toLocalDateTime() : null)
                    .reason(p.getFiledAt() != null ? "Filing " + p.getFiledAt() : "Signal history")
                    .build();
        }
    }

