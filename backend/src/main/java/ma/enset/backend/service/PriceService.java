package ma.enset.backend.service;

import ma.enset.backend.dto.PriceDTO;
import ma.enset.backend.entity.Company;
import ma.enset.backend.exception.ResourceNotFoundException;
import ma.enset.backend.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class PriceService {

    private final CompanyRepository companyRepository;
    private final WebClient.Builder webClientBuilder;

    @Value("${finpulse.alphavantage.api-key:demo}")
    private String apiKey;

    /** Cache simple en mémoire : ticker → (price, timestamp) */
    private final Map<String, CachedPrice> priceCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 5 * 60 * 1000; // 5 minutes

    public PriceDTO getPrice(Long companyId) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company", companyId));

        String ticker = company.getTicker();

        // Check cache
        CachedPrice cached = priceCache.get(ticker);
        if (cached != null && System.currentTimeMillis() - cached.timestamp < CACHE_TTL_MS) {
            log.debug("Price cache hit for {}", ticker);
            return cached.dto;
        }

        // Call Alpha Vantage
        try {
            PriceDTO dto = fetchFromAlphaVantage(ticker);
            priceCache.put(ticker, new CachedPrice(dto, System.currentTimeMillis()));
            return dto;
        } catch (Exception e) {
            log.warn("Alpha Vantage call failed for {}: {} — returning mock data", ticker, e.getMessage());
            return mockPrice(ticker);
        }
    }

    private PriceDTO fetchFromAlphaVantage(String ticker) {
        String url = "https://www.alphavantage.co/query"
                + "?function=GLOBAL_QUOTE"
                + "&symbol=" + ticker
                + "&apikey=" + apiKey;

        Map<?, ?> response = webClientBuilder.build()
                .get().uri(url)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response == null) throw new RuntimeException("Empty response from Alpha Vantage");

        @SuppressWarnings("unchecked")
        Map<String, String> quote = (Map<String, String>) response.get("Global Quote");
        if (quote == null || quote.isEmpty()) throw new RuntimeException("No quote data for " + ticker);

        double price     = parseDouble(quote.get("05. price"));
        double change    = parseDouble(quote.get("09. change"));
        double changePct = parseDouble(quote.get("10. change percent").replace("%", ""));

        return PriceDTO.builder()
                .ticker(ticker)
                .price(price)
                .change24h(change)
                .changePct24h(changePct)
                .currency("USD")
                .build();
    }

    private double parseDouble(String val) {
        if (val == null || val.isBlank()) return 0.0;
        try { return Double.parseDouble(val.trim()); }
        catch (NumberFormatException e) { return 0.0; }
    }

    /** Données fictives si Alpha Vantage ne répond pas ou si la clé est "demo" */
    private PriceDTO mockPrice(String ticker) {
        double basePrice = switch (ticker) {
            case "AAPL"  -> 189.50;
            case "TSLA"  -> 245.30;
            case "NVDA"  -> 875.20;
            case "MSFT"  -> 420.15;
            case "GOOGL" -> 175.80;
            case "META"  -> 510.60;
            case "AMZN"  -> 195.40;
            default      -> 100.0 + Math.random() * 200;
        };
        double changePct = (Math.random() - 0.5) * 4;  // ±2%
        double change    = basePrice * changePct / 100;

        return PriceDTO.builder()
                .ticker(ticker)
                .price(Math.round(basePrice * 100.0) / 100.0)
                .change24h(Math.round(change * 100.0) / 100.0)
                .changePct24h(Math.round(changePct * 100.0) / 100.0)
                .currency("USD")
                .build();
    }

    private record CachedPrice(PriceDTO dto, long timestamp) {}
}
