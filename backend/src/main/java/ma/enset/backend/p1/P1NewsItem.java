package ma.enset.backend.p1;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class P1NewsItem {

    private String headline;
    private String source;

    @JsonProperty("published_at")
    private OffsetDateTime publishedAt;

    @JsonProperty("sentiment_score")
    private Double sentimentScore;
}
