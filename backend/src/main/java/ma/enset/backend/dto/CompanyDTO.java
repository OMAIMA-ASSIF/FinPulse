package ma.enset.backend.dto;
import lombok.*;
import ma.enset.backend.entity.Company;

import java.time.LocalDateTime;
import ma.enset.backend.p1.P1CompanyIdentity;
import ma.enset.backend.p1.P1ScoreResponse;
import ma.enset.backend.util.SentimentUtils;

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
        private String lastUpdate;
        private String nciLabel;       // HIGH / MEDIUM / LOW
        private String riskLevel;      // computed risk label

        public static CompanyDTO from(Company c) {
            Long publicId = c.getIngestionCompanyId() != null ? c.getIngestionCompanyId() : c.getId();
            return CompanyDTO.builder()
                    .id(publicId)
                    .ticker(c.getTicker())
                    .name(c.getName())
                    .sector(c.getSector())
                    .nciGlobal(c.getNciGlobal())
                    .sentimentAvg(c.getSentimentAvg())
                    .lastUpdate(c.getLastUpdate() != null ? c.getLastUpdate().toString() : null)
                    .nciLabel(nciLabel(c.getNciGlobal()))
                    .riskLevel(riskLevel(c.getNciGlobal()))
                    .build();
        }

        public static CompanyDTO fromP1(P1ScoreResponse score) {
            Float nci = score.getCompositeRiskScore() != null
                    ? score.getCompositeRiskScore().floatValue() : null;
            Float sentiment = SentimentUtils.averageFromNews(score.getRecentNews());
            String updated = score.getScoredAt() != null
                    ? score.getScoredAt().toLocalDateTime().toString() : LocalDateTime.now().toString();
            return CompanyDTO.builder()
                    .id(score.getCompanyId())
                    .ticker(score.getTicker())
                    .name(score.getCompanyName())
                    .sector(score.getSector())
                    .nciGlobal(nci)
                    .sentimentAvg(sentiment)
                    .lastUpdate(updated)
                    .nciLabel(nciLabel(nci))
                    .riskLevel(riskLevel(nci))
                    .build();
        }

        public static CompanyDTO fromP1Identity(P1CompanyIdentity identity, P1ScoreResponse scoreOrNull) {
            if (scoreOrNull != null) {
                return fromP1(scoreOrNull);
            }
            return CompanyDTO.builder()
                    .id(identity.getId())
                    .ticker(identity.getTicker())
                    .name(identity.getName())
                    .sector(null)
                    .nciGlobal(null)
                    .sentimentAvg(null)
                    .lastUpdate(LocalDateTime.now().toString())
                    .nciLabel("UNKNOWN")
                    .riskLevel("UNKNOWN")
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
