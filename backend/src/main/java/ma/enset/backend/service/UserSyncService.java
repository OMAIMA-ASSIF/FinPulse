package ma.enset.backend.service;


import ma.enset.backend.entity.User;
import ma.enset.backend.enums.ProfileType;
import ma.enset.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

/**
 * Synchronisation "lazy" entre Keycloak et PostgreSQL.
 *
 * Pattern utilisé :
 * - Keycloak est la source de vérité pour l'identité (auth)
 * - PostgreSQL est la source de vérité pour les données métier
 *   (profil, stratégies, alertes)
 *
 * Au premier accès d'un utilisateur authentifié, on crée
 * automatiquement son entrée en base si elle n'existe pas.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserSyncService {

    private final UserRepository userRepository;

    /**
     * Récupère ou crée l'utilisateur en base à partir du token JWT Keycloak.
     * Appelé à chaque requête authentifiée via le CurrentUserService.
     */
    @Transactional
    public User getOrCreateUser(Jwt jwt) {
        String keycloakId = jwt.getSubject();  // le "sub" du JWT = ID Keycloak

        return userRepository.findByKeycloakId(keycloakId)
                .orElseGet(() -> createUserFromJwt(jwt));
    }

    /**
     * Crée l'utilisateur en DB à partir des claims du JWT.
     * Appelé uniquement si l'utilisateur n'existe pas encore en DB.
     */
    private User createUserFromJwt(Jwt jwt) {
        String keycloakId  = jwt.getSubject();
        String username    = jwt.getClaimAsString("preferred_username");
        String email       = jwt.getClaimAsString("email");
        String profileType = extractProfileType(jwt);

        log.info("Création user en DB (lazy sync): keycloakId={}, username={}", keycloakId, username);

        User user = User.builder()
                .keycloakId(keycloakId)
                .username(username)
                .email(email)
                .profileType(ProfileType.valueOf(profileType))
                .build();

        return userRepository.save(user);
    }

    /**
     * Met à jour les données user en base si elles ont changé dans Keycloak.
     * À appeler après un changement de profil.
     */
    @Transactional
    public User syncUser(Jwt jwt) {
        String keycloakId = jwt.getSubject();

        Optional<User> existing = userRepository.findByKeycloakId(keycloakId);
        if (existing.isEmpty()) {
            return createUserFromJwt(jwt);
        }

        User user = existing.get();
        String newEmail = jwt.getClaimAsString("email");
        String newProfile = extractProfileType(jwt);

        // Synchroniser si les données ont changé
        boolean changed = false;
        if (!user.getEmail().equals(newEmail)) {
            user.setEmail(newEmail);
            changed = true;
        }
        if (!user.getProfileType().name().equals(newProfile)) {
            user.setProfileType(ProfileType.valueOf(newProfile));
            changed = true;
        }

        if (changed) {
            log.info("Synchronisation user: keycloakId={}", keycloakId);
            return userRepository.save(user);
        }

        return user;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String extractProfileType(Jwt jwt) {
        // Récupérer depuis les claims Keycloak (attribut custom "profile_type")
        String profileType = jwt.getClaimAsString("profile_type");

        // Fallback : vérifier les rôles realm
        if (profileType == null) {
            Map<String, Object> realmAccess = jwt.getClaim("realm_access");
            if (realmAccess != null) {
                @SuppressWarnings("unchecked")
                java.util.List<String> roles = (java.util.List<String>) realmAccess.get("roles");
                if (roles != null && roles.contains("SPECULATEUR")) {
                    return "SPECULATEUR";
                }
            }
        }

        // Valeur par défaut
        if (profileType == null || (!profileType.equals("PRUDENT") && !profileType.equals("SPECULATEUR"))) {
            return "PRUDENT";
        }

        return profileType;
    }
}

