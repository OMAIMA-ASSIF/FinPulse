package ma.enset.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.enset.backend.config.KeycloakAdminConfig;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import org.springframework.web.client.RestTemplate;

import jakarta.ws.rs.core.Response;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class KeycloakAdminService {

    private final KeycloakAdminConfig config;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${keycloak.realm}")        private String realm;
    @Value("${keycloak.server-url}")   private String serverUrl;
    @Value("${keycloak.client-id}")    private String clientId;
    @Value("${keycloak.client-secret}") private String clientSecret;

    // ── CREATE ──────────────────────────────────────────────────────────────
    public String createUser(String username, String email, String password, String profileType) {
        UsersResource ur = getKeycloak().realm(realm).users();
        if (!ur.searchByUsername(username, true).isEmpty())
            throw new KeycloakUserException("USERNAME_EXISTS", "Username already taken");
        if (!ur.searchByEmail(email, true).isEmpty())
            throw new KeycloakUserException("EMAIL_EXISTS", "Email already registered");

        UserRepresentation user = new UserRepresentation();
        user.setUsername(username); user.setEmail(email);
        user.setEmailVerified(true); user.setEnabled(true);
        user.setAttributes(Map.of("profile_type", List.of(profileType)));

        Response response = ur.create(user);
        if (response.getStatus() != 201)
            throw new RuntimeException("Keycloak create failed: " + response.readEntity(String.class));

        String location   = response.getHeaderString("Location");
        String keycloakId = location.substring(location.lastIndexOf('/') + 1);
        setPassword(keycloakId, password, false);
        assignRoles(keycloakId, "USER", profileType);
        return keycloakId;
    }

    // ── CHANGE PASSWORD ──────────────────────────────────────────────────────
    public void changePassword(String keycloakId, String username, String currentPwd, String newPwd) {
        setPassword(keycloakId, newPwd, false);
        log.info("Password changed for keycloakId={}", keycloakId);
    }


    private void setPassword(String keycloakId, String password, boolean temporary) {
        CredentialRepresentation cred = new CredentialRepresentation();
        cred.setType(CredentialRepresentation.PASSWORD);
        cred.setValue(password); cred.setTemporary(temporary);
        getKeycloak().realm(realm).users().get(keycloakId).resetPassword(cred);
    }

    // ── DELETE ───────────────────────────────────────────────────────────────
    public void deleteUser(String keycloakId) {
        try {
            Response r = getKeycloak().realm(realm).users().delete(keycloakId);
            log.info("Keycloak user deleted: {} — status: {}", keycloakId, r.getStatus());
        } catch (Exception e) {
            log.error("Keycloak delete failed for {}: {}", keycloakId, e.getMessage());
            throw new RuntimeException("Keycloak deletion failed", e);
        }
    }

    // ── UPDATE PROFILE TYPE ──────────────────────────────────────────────────
    public void updateProfileType(String keycloakId, String profileType) {
        UsersResource ur   = getKeycloak().realm(realm).users();
        UserRepresentation u = ur.get(keycloakId).toRepresentation();
        Map<String, List<String>> attrs = u.getAttributes() != null
                ? new HashMap<>(u.getAttributes()) : new HashMap<>();
        attrs.put("profile_type", List.of(profileType));
        u.setAttributes(attrs);
        ur.get(keycloakId).update(u);
        removeRole(keycloakId, "PRUDENT");
        removeRole(keycloakId, "SPECULATEUR");
        assignRoles(keycloakId, profileType);
    }

    // ── UPDATE INFO ──────────────────────────────────────────────────────────
    public void updateUserInfo(String keycloakId, String username, String email) {

        UsersResource ur = getKeycloak().realm(realm).users();
        UserResource userResource = ur.get(keycloakId);

        UserRepresentation u = userResource.toRepresentation();

        if (username != null && !username.isBlank()) {
            u.setUsername(username.trim());
        }

        if (email != null && !email.isBlank()) {
            u.setEmail(email.trim().toLowerCase());
            u.setEmailVerified(true);
        }

        try {
            userResource.update(u);
            log.info("Keycloak update SUCCESS");
        } catch (Exception e) {
            log.error("Keycloak update FAILED", e);
        }
    }

    // ── ROLES ────────────────────────────────────────────────────────────────
    private void assignRoles(String keycloakId, String... roleNames) {
        var realmRes = getKeycloak().realm(realm);
        List<RoleRepresentation> roles = Arrays.stream(roleNames)
                .map(name -> { try { return realmRes.roles().get(name).toRepresentation(); }
                catch (Exception e) { log.warn("Role {} not found", name); return null; } })
                .filter(Objects::nonNull).toList();
        if (!roles.isEmpty()) realmRes.users().get(keycloakId).roles().realmLevel().add(roles);
    }

    private void removeRole(String keycloakId, String roleName) {
        try {
            var rr   = getKeycloak().realm(realm);
            var role = rr.roles().get(roleName).toRepresentation();
            rr.users().get(keycloakId).roles().realmLevel().remove(List.of(role));
        } catch (Exception e) { log.warn("Could not remove role {}: {}", roleName, e.getMessage()); }
    }

    private Keycloak getKeycloak() { return config.keycloakAdminClient(); }

    public static class KeycloakUserException extends RuntimeException {
        private final String errorCode;
        public KeycloakUserException(String errorCode, String message) { super(message); this.errorCode = errorCode; }
        public String getErrorCode() { return errorCode; }
    }
}
