package ma.enset.backend.dto;
import lombok.*;
import ma.enset.backend.entity.Company;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanyDTO {

        private Long id;
        private String ticker;
        private String name;
        private String sector;
        private Float nciGlobal;
        private Float sentimentAvg;
        private LocalDateTime lastUpdate;
        private String nciLabel;       // HIGH / MEDIUM / LOW
        private String riskLevel;      // computed risk label

        public static CompanyDTO from(Company c) {
            return CompanyDTO.builder()
                    .id(c.getId())
                    .ticker(c.getTicker())
                    .name(c.getName())
                    .sector(c.getSector())
                    .nciGlobal(c.getNciGlobal())
                    .sentimentAvg(c.getSentimentAvg())
                    .lastUpdate(c.getLastUpdate())
                    .nciLabel(nciLabel(c.getNciGlobal()))
                    .riskLevel(riskLevel(c.getNciGlobal()))
                    .build();
        }

        private static String nciLabel(Float nci) {
            if (nci == null) return "UNKNOWN";
            if (nci >= 0.7) return "HIGH";
            if (nci >= 0.4) return "MEDIUM";
            return "LOW";
        }

        private static String riskLevel(Float nci) {
            if (nci == null) return "UNKNOWN";
            if (nci >= 0.7) return "LOW_RISK";
            if (nci >= 0.4) return "MEDIUM_RISK";
            return "HIGH_RISK";
        }
    }
