package com.hybrid.gateway.filters;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class AuthenticationFilter implements GlobalFilter, Ordered {

    private final boolean jwtEnabled;
    private final JWTVerifier jwtVerifier;

    public AuthenticationFilter(
            @Value("${gateway.auth.jwt.enabled:true}") boolean jwtEnabled,
            @Value("${gateway.auth.jwt.secret}") String jwtSecret,
            @Value("${gateway.auth.jwt.issuer:}") String jwtIssuer
    ) {
        this.jwtEnabled = jwtEnabled;
        Algorithm algorithm = Algorithm.HMAC256(jwtSecret);
        this.jwtVerifier = (jwtIssuer == null || jwtIssuer.isBlank())
                ? JWT.require(algorithm).build()
                : JWT.require(algorithm).withIssuer(jwtIssuer).build();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, org.springframework.cloud.gateway.filter.GatewayFilterChain chain) {
        if (!jwtEnabled || isPublicPath(exchange.getRequest().getPath().value())) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange, "Missing or invalid Authorization header");
        }

        String token = authHeader.substring("Bearer ".length()).trim();
        if (token.isBlank()) {
            return unauthorized(exchange, "JWT token is empty");
        }

        try {
            DecodedJWT jwt = jwtVerifier.verify(token);
            if (!isAuthorizedForPath(exchange.getRequest().getPath().value(), jwt)) {
                return forbidden(exchange, "Insufficient scope/role for route");
            }
        } catch (Exception ex) {
            return unauthorized(exchange, "JWT validation failed");
        }

        return chain.filter(exchange);
    }

    private boolean isPublicPath(String path) {
        return path.startsWith("/actuator")
                || path.startsWith("/fallback")
                || path.startsWith("/health");
    }

    private boolean isAuthorizedForPath(String path, DecodedJWT jwt) {
        String requiredPermission = requiredPermission(path);
        if (requiredPermission == null) {
            return true;
        }

        Set<String> permissions = extractPermissions(jwt);
        return permissions.contains(requiredPermission);
    }

    private String requiredPermission(String path) {
        if (path.startsWith("/search") || path.startsWith("/facets")) {
            return "search:read";
        }
        if (path.startsWith("/api/rank")) {
            return "ranking:write";
        }
        if (path.startsWith("/api/fusion")) {
            return "fusion:write";
        }
        return null;
    }

    private Set<String> extractPermissions(DecodedJWT jwt) {
        Set<String> permissions = new HashSet<>();
        String scope = jwt.getClaim("scope").asString();
        if (scope != null && !scope.isBlank()) {
            for (String s : scope.split("\\s+")) {
                if (!s.isBlank()) {
                    permissions.add(s.trim());
                }
            }
        }

        List<String> scopes = jwt.getClaim("scopes").asList(String.class);
        if (scopes != null) {
            permissions.addAll(scopes);
        }

        List<String> roles = jwt.getClaim("roles").asList(String.class);
        if (roles != null) {
            permissions.addAll(roles);
        }
        return permissions;
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String reason) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().add("X-Auth-Error", reason);
        return exchange.getResponse().setComplete();
    }

    private Mono<Void> forbidden(ServerWebExchange exchange, String reason) {
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().add("X-Auth-Error", reason);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
