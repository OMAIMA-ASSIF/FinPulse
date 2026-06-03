package ma.enset.backend.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder

public class DashboardDTO {

        private Double globalNciAverage;
        private Double globalSentimentAverage;
        private List<CompanyDTO> topCompanies;
        private List<CompanyDTO> atRiskCompanies;
        private long totalCompanies;
        private LocalDateTime generatedAt;
    }

