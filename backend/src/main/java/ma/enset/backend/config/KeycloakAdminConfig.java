package ma.enset.backend.config;

import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KeycloakAdminConfig {

    @Value("${keycloak.server-url}")
    private String serverUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.client-id}")
    private String clientId;

    @Value("${keycloak.client-secret}")
    private String clientSecret;

    /**
     * Client Keycloak Admin qui s'authentifie avec les credentials
     * du Service Account du client finpulse-backend.
     *
     * Utilise le flow client_credentials (machine-to-machine).
     */
    @Bean
    public Keycloak keycloakAdminClient() {
        return KeycloakBuilder.builder()
                .serverUrl(serverUrl)
                .realm(realm)                          // realm finpulse
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                .clientId(clientId)                    // finpulse-backend
                .clientSecret(clientSecret)
                .build();
    }
}
