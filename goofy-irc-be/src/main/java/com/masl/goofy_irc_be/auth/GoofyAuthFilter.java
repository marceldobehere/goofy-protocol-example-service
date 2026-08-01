package com.masl.goofy_irc_be.auth;

import com.masl.goofy_protocol_core.crypto.connected.HandleCrypto;
import com.masl.goofy_protocol_core.crypto.connected.request.BasicRequestValidator;
import com.masl.goofy_protocol_core.crypto.connected.request.SignedRequest;
import com.masl.goofy_protocol_core.crypto.connected.request.SignedRequestValidator;
import com.masl.goofy_protocol_core.crypto.exceptions.PubSplitKeyNotFound;
import com.masl.goofy_irc_be.crypto.IrcHandleCrypto;
import com.masl.goofy_irc_be.entity.User;
import com.masl.goofy_irc_be.exception.client.ContentTooLarge;
import com.masl.goofy_irc_be.exception.client.InvalidSignature;
import com.masl.goofy_irc_be.exception.server.PublicKeyLookupFailed;
import com.masl.goofy_irc_be.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class GoofyAuthFilter extends OncePerRequestFilter {
    private final SignedRequestValidator validator = new BasicRequestValidator();
    private final HandleCrypto handleCrypto;
    private final UserRepository userRepository;
    private final int maxRequestSizeBytes;
    private final boolean disableUniqueIdCheck;
    private final HandlerExceptionResolver resolver;

    public GoofyAuthFilter(IrcHandleCrypto handleCrypto, UserRepository userRepository, Environment env,
                           @Value("${goofy.auth.max-request-bytes}") int maxRequestBytes,
                           @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        this.handleCrypto = handleCrypto;
        this.userRepository = userRepository;
        this.disableUniqueIdCheck = env.acceptsProfiles(Profiles.of("test")); // Important for Perf Testing
        this.maxRequestSizeBytes = maxRequestBytes;
        this.resolver = resolver;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)  {
        try {
            Map<String, String> headers = Collections.list(request.getHeaderNames())
                    .stream().collect(Collectors.toMap(h -> h, request::getHeader));

            // If the Request is not signed, we don't need to check it
            if (!SignedRequest.hasAllRequestHeaders(headers)) {
                SecurityContextHolder.getContext().setAuthentication(new GoofyAuth());
                filterChain.doFilter(request, response);
                return;
            }

            // Cache Request So body can be read without issues
            ContentCachingRequestWrapper _wrapped = new ContentCachingRequestWrapper(request, maxRequestSizeBytes);
            byte[] body;
            try (var in = _wrapped.getInputStream()) {
                body = in.readNBytes(maxRequestSizeBytes + 1);

                // Read the remaining data without storing it
                // This is NEEDED FOR THE RESPONSE / ERROR HANDLING TO WORK PROPERLY (don't ask me why)
                byte[] buffer = new byte[8192];

                //noinspection StatementWithEmptyBody
                while (in.read(buffer) != -1);
            }

            if (body.length > maxRequestSizeBytes)
                throw new ContentTooLarge();

            // Parse Request
            SignedRequest req;
            try {
                req = SignedRequest.fromRequestHeaders(headers, request.getMethod(), request.getRequestURI(), body, handleCrypto);
            } catch (PubSplitKeyNotFound e) {
                throw new PublicKeyLookupFailed(e.handle);
            }

            // Check Validity
            SignedRequest.SignedRequestValidity valid = req.isValid(handleCrypto, validator);
            if (!valid.equals(SignedRequest.SignedRequestValidity.VALID))
                throw new InvalidSignature(valid);

            // Invalidate ID
            if (!disableUniqueIdCheck)
                validator.invalidateUniqueId(req.uniqueId());

            // Get User Data and Create Authentication
            User user = userRepository.findByHandle(req.handle());
            boolean isIdentity = user != null; // Could have the Role be exclusive, but I'd rather explicitly check against it in the respective Endpoints to avoid misunderstandings
            boolean isUser = user != null;
            boolean isAdmin = user != null && user.isAdmin();
            boolean isRestricted = user != null && !user.isAdmin() && user.isRestricted();

            SecurityContextHolder.getContext().setAuthentication(new GoofyAuth(req, isIdentity, isUser, isAdmin, isRestricted));

            // Fix Body for Filter
            RequestBodyContentWrapper wrapped = new RequestBodyContentWrapper(_wrapped, maxRequestSizeBytes);
            wrapped.prepareInputStream();

            // Continue
            filterChain.doFilter(wrapped, response);
        } catch (Exception e) {
            resolver.resolveException(request, response, null, e);
        }

        SecurityContextHolder.clearContext();
    }
}
