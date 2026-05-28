package ma.enset.backend.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class StrategyRequestDTO {
    @NotNull(message = "Company ID is required")
    private Long companyId;

    @Size(max = 1000)
    private String userArgument;
}
