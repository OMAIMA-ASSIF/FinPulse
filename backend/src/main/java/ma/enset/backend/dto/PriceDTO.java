package ma.enset.backend.dto;

import lombok.*;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PriceDTO {
    private String ticker;
    private double price;
    private double change24h;
    private double changePct24h;
    private String currency;
}
