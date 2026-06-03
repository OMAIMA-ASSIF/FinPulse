package ma.enset.backend.dto;

import lombok.*;
import java.util.List;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class StrategyReportDTO {
    private Long         id;
    private String       ticker;
    private String       companyName;
    private String       thesis;
    private List<String> bullCase;
    private List<String> risks;
    private List<String> secContradictions;
    private String       historicalInsight;
    private String       recommendation;   // BUY / HOLD / AVOID
    private Float        nciPersonalized;
    private String       rawAiResponse;
    private String       generatedAt;
}
