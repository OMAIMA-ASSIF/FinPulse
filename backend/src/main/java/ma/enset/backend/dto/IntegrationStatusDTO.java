package ma.enset.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntegrationStatusDTO {
    private String backend;
    private String p1ApiUrl;
    private boolean p1Reachable;
    private int p1CompanyCount;
    private String message;
}
