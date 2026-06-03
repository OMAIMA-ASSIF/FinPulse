package ma.enset.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FavoriteCompanyDTO {
    private Long id;
    private String ticker;
    private String companyName;
    private LocalDateTime addedAt;
}
