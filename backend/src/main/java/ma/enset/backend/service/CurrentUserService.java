package ma.enset.backend.service;

import ma.enset.backend.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

/**
 * Service central pour accéder à l'utilisateur authentifié.
 *
 * Usage dans n'importe quel Controller ou Service :
 *   User currentUser = currentUserService.getCurrentUser();
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CurrentUserService {

    private final UserSyncService userSyncService;

    /**
     * Retourne l'entité User en base correspondant au JWT actuel.
     * Crée l'utilisateur en base si c'est sa première connexion (lazy sync).
     */
    public User getCurrentUser() {
        Jwt jwt = extractJwt();
        return userSyncService.getOrCreateUser(jwt);
    }

    /**
     * Retourne le JWT brut.
     * Utile pour accéder aux claims directement.
     */
    public Jwt getCurrentJwt() {
        return extractJwt();
    }

    /**
     * Retourne le keycloak_id (sub) de l'utilisateur courant.
     */
    public String getCurrentKeycloakId() {
        return extractJwt().getSubject();
    }

    /**
     * Retourne le username de l'utilisateur courant.
     */
    public String getCurrentUsername() {
        return extractJwt().getClaimAsString("preferred_username");
    }

    /**
     * Vérifie si l'utilisateur courant a un rôle donné.
     */
    public boolean hasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + role.toUpperCase()));
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private Jwt extractJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken();
        }

        throw new IllegalStateException(
                "Aucun utilisateur authentifié dans le contexte de sécurité. " +
                        "Assurez-vous que la route est protégée par authGuard."
        );
    }
}

