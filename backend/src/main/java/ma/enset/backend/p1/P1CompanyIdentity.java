package ma.enset.backend.p1;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class P1CompanyIdentity {

    private Long id;
    private String name;
    private String ticker;
    private String cik;

    @JsonProperty("is_active")
    private Boolean isActive;
}
