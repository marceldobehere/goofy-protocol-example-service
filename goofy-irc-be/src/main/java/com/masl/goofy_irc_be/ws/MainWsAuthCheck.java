package com.masl.goofy_irc_be.ws;

import com.masl.goofy_irc_be.auth.GoofyAuthFilter;
import com.masl.goofy_irc_be.auth.GoofyAuthUser;
import com.masl.goofy_irc_be.entity.CachedKeyHandleEntry;
import com.masl.goofy_irc_be.exception.client.InvalidSignature;
import com.masl.goofy_irc_be.exception.server.PublicKeyLookupFailed;
import com.masl.goofy_irc_be.repository.CachedKeyHandleRepository;
import com.masl.goofy_protocol_core.crypto.connected.request.SignedRequest;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

// TODO: Test properly
@Component
public class MainWsAuthCheck implements HandshakeInterceptor {
    private static final Logger log = LoggerFactory.getLogger(MainWsAuthCheck.class);

    private final CachedKeyHandleRepository cachedKeyHandleRepository;
    private final GoofyAuthFilter goofyAuthFilter;

    public MainWsAuthCheck(CachedKeyHandleRepository cachedKeyHandleRepository, GoofyAuthFilter goofyAuthFilter) {
        this.cachedKeyHandleRepository = cachedKeyHandleRepository;
        this.goofyAuthFilter = goofyAuthFilter;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, @NonNull ServerHttpResponse response, @NonNull WebSocketHandler handler, @NonNull Map<String, Object> attrs) throws IOException {
        log.debug("Performing WebSocket handshake authentication check for request: {}", request.getURI().getPath());

        // Extract "headers
        Map<String, String> headers = new HashMap<>();
        String[] parts = request.getURI().getQuery().split(Pattern.quote("&"));
        for (String part : parts) {
            String[] keyValue = part.split(Pattern.quote("="));
            if (keyValue.length == 2)
                headers.put(URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8));
        }

        try {
            // Try to authenticate
            GoofyAuthUser authUser = (GoofyAuthUser)goofyAuthFilter.getGoofyAuthFromSignedRequest(headers, request.getMethod().name(), request.getURI().getPath(), null).getPrincipal();
            if (authUser == null)
                throw new InvalidSignature(SignedRequest.SignedRequestValidity.MISSING_PARTS);

            // Check if user has domain attached, only users with a domain are allowed
            CachedKeyHandleEntry entry = cachedKeyHandleRepository.findByHandle(authUser.getHandle());
            if (entry == null || entry.getHandleDomain() == null)
                throw new PublicKeyLookupFailed(authUser.getHandle());

            // Store AuthUser
            attrs.put("authUser", authUser);
            return true;
        } catch (PublicKeyLookupFailed | InvalidSignature ex) {
            // Sadly it seems, that I cant use my GlobalExceptionHandler here so I'll do it manually
            final ObjectMapper objectMapper = new ObjectMapper();
            final String res = objectMapper.writeValueAsString(Map.of(
                    "errorCode", ex.errorCode,
                    "message", ex.message,
                    "details", ex.errorDetails));

            response.setStatusCode(HttpStatusCode.valueOf(ex.httpCode));
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            response.getBody().write(res.getBytes());
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, @NonNull ServerHttpResponse response, @NonNull WebSocketHandler handler, Exception ex) {
        log.debug("WebSocket handshake completed for request: {}", request.getURI());
    }
}
