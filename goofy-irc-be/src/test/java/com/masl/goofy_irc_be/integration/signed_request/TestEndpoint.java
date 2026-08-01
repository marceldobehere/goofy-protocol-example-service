package com.masl.goofy_irc_be.integration.signed_request;

import com.masl.goofy_irc_be.auth.GoofyAuthUser;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Only accessible during test execution
@RestController
@Profile({"test"})
@RequestMapping("/api/test")
@Hidden
public class TestEndpoint {
    @GetMapping("/test-guest")
    public String testGuest() {
        return "Hello, Guest";
    }

    @GetMapping("/test-outsider")
    @PreAuthorize("hasRole('ROLE_OUTSIDE_ENTITY')")
    public String testOutsider(@AuthenticationPrincipal GoofyAuthUser auth) {
        return "Hello, " + auth.getHandle() + " (Outsider)";
    }

    @GetMapping("/test-user")
    @PreAuthorize("hasRole('ROLE_REGISTERED_USER')")
    public String testUser(@AuthenticationPrincipal GoofyAuthUser auth) {
        return "Hello, " + auth.getHandle() + " (User)";
    }

    @GetMapping("/test-admin")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public String testAdmin(@AuthenticationPrincipal GoofyAuthUser auth) {
        return "Hello, " + auth.getHandle() + " (Admin)";
    }
}
