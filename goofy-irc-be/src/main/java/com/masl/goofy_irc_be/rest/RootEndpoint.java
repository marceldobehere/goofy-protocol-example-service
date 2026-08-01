package com.masl.goofy_irc_be.rest;

import com.masl.goofy_irc_be.exception.base.swagger.IrcEndpoint;
import com.masl.goofy_irc_be.properties.GeneralProperties;
import io.swagger.v3.oas.annotations.Hidden;
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
    public ResponseEntity<String> index() {
        URI redirectUri = UriComponentsBuilder
                .fromUriString(generalProperties.getFrontendUrl())
                .queryParam("overrideBackendUrl", generalProperties.getUrl())
                .build(true)
                .toUri();

        return ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT)
                .location(redirectUri)
                .build();
    }
}
