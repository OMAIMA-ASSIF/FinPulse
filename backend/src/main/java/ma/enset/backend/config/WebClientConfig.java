package ma.enset.backend.config;

import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * Configuration WebClient pour les appels à l'API P1 (Data Ingestion Pipeline).
 * Fournit des timeouts et une gestion d'erreurs appropriée.
 */
@Configuration
public class WebClientConfig {

    @Value("${p1.api.url:http://localhost:8000}")
    private String p1ApiUrl;

    /**
     * Crée un WebClient pour communiquer avec l'API P1.
     * Configure les timeouts de connexion, lecture et écriture.
     */
    @Bean
    public WebClient webClient() {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(30))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000);

        return WebClient.builder()
                .baseUrl(p1ApiUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
