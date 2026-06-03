package ma.enset.backend.p1;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class P1MarketSnapshot {

    @JsonProperty("close_price")
    private Double closePrice;
}
