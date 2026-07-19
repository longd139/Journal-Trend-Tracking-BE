package com.sra.journal_tracking.security;

import java.io.IOException;
import java.util.Set;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.sra.journal_tracking.repository.jpa.UserSessionRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * JWT authentication filter — extracts and validates JWT from request.
 * <p>
 * IMPORTANT: This class does NOT have @Component — it must only be registered
 * via {@code http.addFilterBefore()} in {@link SecurityConfig}. Spring Boot
 * auto-registering it as a generic servlet filter (via @Component) causes
 * double-registration, which breaks {@code web.ignoring()} and causes
 * {@code AuthorizationDeniedException} for public paths like /api/v1/gap/**.
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/auth/", "/api/test/", "/api/public/",
            "/api/graphs/", "/api/v1/gap/", "/api/v1/gap",
            "/swagger-ui/", "/v3/api-docs/"
    );

    private final JwtTokenProvider tokenProvider;
    private final CustomUserDetailsService customUserDetailsService;
    private final UserSessionRepository userSessionRepository;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        try {
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
                // Check if the session is still active (not logged out)
                String tokenHash = tokenProvider.hashToken(jwt);
                boolean isSessionActive = userSessionRepository.findByTokenHash(tokenHash).isPresent();

                if (isSessionActive) {
                    String email = tokenProvider.getUsernameFromJWT(jwt);

                    UserDetails userDetails = customUserDetailsService.loadUserByUsername(email);
                    if (userDetails.isEnabled()) {
                        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities());
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                }
            }
        } catch (Exception ex) {
            // Log exception
            logger.error("Could not set user authentication in security context", ex);
            // ex.printStackTrace();
        }

        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        // 1. Try Authorization header (standard REST requests)
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        // 2. Try ?token= query param (SSE EventSource cannot set custom headers)
        String tokenParam = request.getParameter("token");
        if (StringUtils.hasText(tokenParam)) {
            return tokenParam;
        }
        return null;
    }
}
