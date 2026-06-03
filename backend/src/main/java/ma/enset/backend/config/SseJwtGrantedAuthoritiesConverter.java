package ma.enset.backend.config;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class SseJwtGrantedAuthoritiesConverter {
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Set<GrantedAuthority> authorities = new HashSet<>();
        extractRealmRoles(jwt).forEach(r ->
                authorities.add(new SimpleGrantedAuthority("ROLE_" + r.toUpperCase())));
        extractClientRoles(jwt, "finpulse-backend").forEach(r ->
                authorities.add(new SimpleGrantedAuthority("ROLE_" + r.toUpperCase())));
        return authorities;
    }
    @SuppressWarnings("unchecked")
    private List<String> extractRealmRoles(Jwt jwt) {
        try {
            Map<String, Object> ra = jwt.getClaim("realm_access");
            if (ra == null) return Collections.emptyList();
            return (List<String>) ra.getOrDefault("roles", Collections.emptyList());
        } catch (Exception e) { return Collections.emptyList(); }
    }
    @SuppressWarnings("unchecked")
    private List<String> extractClientRoles(Jwt jwt, String clientId) {
        try {
            Map<String, Object> resourceAccess = jwt.getClaim("resource_access");
            if (resourceAccess == null) return Collections.emptyList();
            Map<String, Object> ca = (Map<String, Object>) resourceAccess.get(clientId);
            if (ca == null) return Collections.emptyList();
            return (List<String>) ca.getOrDefault("roles", Collections.emptyList());
        } catch (Exception e) { return Collections.emptyList(); }
    }
}
