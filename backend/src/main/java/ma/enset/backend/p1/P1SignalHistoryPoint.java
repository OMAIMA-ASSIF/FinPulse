package ma.enset.backend.p1;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class P1SignalHistoryPoint {

    @JsonProperty("filing_id")
    private Long filingId;

    @JsonProperty("signal_value")
    private Double signalValue;

    @JsonProperty("computed_at")
    private OffsetDateTime computedAt;

    @JsonProperty("filed_at")
    private LocalDate filedAt;
}
