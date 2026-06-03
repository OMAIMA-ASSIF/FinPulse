package ma.enset.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Collection;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SseTokenAuthFilter extends OncePerRequestFilter {
    private final JwtDecoder jwtDecoder;
    private final SseJwtGrantedAuthoritiesConverter authoritiesConverter;

    public SseTokenAuthFilter(JwtDecoder jwtDecoder, SseJwtGrantedAuthoritiesConverter authoritiesConverter) {
        this.jwtDecoder = jwtDecoder;
        this.authoritiesConverter = authoritiesConverter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/stream/");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String token = request.getParameter("token");
        if (token == null || token.isBlank()) {
            log.warn("[SSE] No token for: {}", request.getRequestURI());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "SSE token missing");
            return;
        }
        try {
            Jwt jwt = jwtDecoder.decode(token);
            @SuppressWarnings("unchecked")
            JwtAuthenticationToken auth = new JwtAuthenticationToken(
                    jwt, (Collection<org.springframework.security.core.GrantedAuthority>)
                    authoritiesConverter.convert(jwt));
            auth.setAuthenticated(true);
            SecurityContextHolder.getContext().setAuthentication(auth);
            chain.doFilter(request, response);
        } catch (JwtException e) {
            SecurityContextHolder.clearContext();
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid SSE token");
        }
    }
}
