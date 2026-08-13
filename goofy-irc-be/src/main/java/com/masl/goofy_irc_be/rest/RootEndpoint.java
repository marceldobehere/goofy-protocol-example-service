package com.masl.goofy_irc_be.rest;

import com.masl.goofy_irc_be.exception.base.swagger.IrcEndpoint;
import com.masl.goofy_irc_be.properties.GeneralProperties;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/")
@Hidden
public class RootEndpoint {
    private final GeneralProperties generalProperties;

    public RootEndpoint(GeneralProperties generalProperties) {
        this.generalProperties = generalProperties;
    }

    @GetMapping
    @IrcEndpoint(summary = "Redirects to the Frontend URL (should be static) <br>Also appends the `overrideBackendUrl` automatically, so the Frontend talks to the correct Backend")
    public ResponseEntity<String> index(HttpServletRequest request) {
        // Important due to HTTP/2 potentially reusing the connection even though it is on a different domain, therefore causing an infinite redirection loop!
        if (!generalProperties.getDomainHost().equals(request.getServerName()))
            return ResponseEntity.status(HttpStatus.MISDIRECTED_REQUEST).build();

        URI redirectUri = UriComponentsBuilder
                .fromUriString(generalProperties.getFrontendUrl())
                .queryParam("overrideBackendUrl", generalProperties.getUrl())
                .build(false)
                .toUri();

        return ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT)
                .location(redirectUri)
                .build();
    }
}
