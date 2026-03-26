package com.skillsync.apigateway.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class JwtValidationFilter implements GlobalFilter, Ordered {

    private static final String AUTH_VALIDATE_PATH = "/auth/validate";

    private final WebClient authServiceWebClient;
    private final ObjectMapper objectMapper;

    public JwtValidationFilter(WebClient authServiceWebClient, ObjectMapper objectMapper) {
        this.authServiceWebClient = authServiceWebClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (isPublicRequest(path, exchange.getRequest().getMethod())) {
            return chain.filter(exchange);
        }

        String authorizationHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorizationHeader) || !authorizationHeader.startsWith("Bearer ")) {
            return unauthorized(exchange);
        }

        return authServiceWebClient.get()
                .uri(AUTH_VALIDATE_PATH)
                .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .flatMap(response -> {
                    JsonNode principal = response.path("data");
                    if (principal.isMissingNode() || principal.isNull()) {
                        return unauthorized(exchange);
                    }
                    return chain.filter(withUserHeaders(exchange, principal));
                })
                .onErrorResume(ex -> unauthorized(exchange));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    private boolean isPublicRequest(String path, HttpMethod method) {
        if (HttpMethod.OPTIONS.equals(method)) {
            return true;
        }

        if (path.startsWith("/actuator/")) {
            return true;
        }

        if ("/auth".equals(path) || path.startsWith("/auth/")) {
            return true;
        }

        if (HttpMethod.GET.equals(method)) {
            // /mentors/me and /mentors/apply require auth
            if ("/mentors/me".equals(path) || "/mentors/apply".equals(path)) {
                return false;
            }
            return "/skills".equals(path)
                    || path.startsWith("/skills/")
                    || "/mentors".equals(path)
                    || path.startsWith("/mentors/");
        }

        return false;
    }

    private ServerWebExchange withUserHeaders(ServerWebExchange exchange, JsonNode principal) {
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header("X-User-Id", principal.path("userId").asText(""))
                .header("X-User-Email", principal.path("email").asText(""))
                .header("X-User-Name", principal.path("name").asText(""))
                .header("X-User-Roles", joinArray(principal.path("roles")))
                .header("X-User-Permissions", joinArray(principal.path("permissions")))
                .build();
        return exchange.mutate().request(mutatedRequest).build();
    }

    private String joinArray(JsonNode node) {
        if (!node.isArray()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        Iterator<JsonNode> iterator = node.elements();
        while (iterator.hasNext()) {
            builder.append(iterator.next().asText());
            if (iterator.hasNext()) {
                builder.append(',');
            }
        }
        return builder.toString();
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(java.util.Map.of(
                    "success", false,
                    "message", "Unauthorized"
            ));
        } catch (Exception ex) {
            body = "{\"success\":false,\"message\":\"Unauthorized\"}".getBytes(StandardCharsets.UTF_8);
        }
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }
}
