package ma.enset.backend.p1;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class P1ScoreResponse {

    @JsonProperty("company_id")
    private Long companyId;

    private String ticker;

    @JsonProperty("company_name")
    private String companyName;

    private String sector;

    @JsonProperty("composite_risk_score")
    private Double compositeRiskScore;

    @JsonProperty("risk_label")
    private String riskLabel;

    @JsonProperty("recent_news")
    private List<P1NewsItem> recentNews;

    private P1MarketSnapshot market;

    @JsonProperty("scored_at")
    private OffsetDateTime scoredAt;
}
