package com.masl.goofy_irc_be.rest;

import com.masl.goofy_irc_be.auth.GoofyAuthUser;
import com.masl.goofy_irc_be.dto.request.RegistrationRequestDto;
import com.masl.goofy_irc_be.dto.response.RegisterStatusDto;
import com.masl.goofy_irc_be.exception.base.swagger.IrcEndpoint;
import com.masl.goofy_irc_be.exception.client.HandleAlreadyRegistered;
import com.masl.goofy_irc_be.exception.client.InvalidRegisterCode;
import com.masl.goofy_irc_be.exception.client.RegistrationCodeAlreadyUsed;
import com.masl.goofy_irc_be.exception.client.RegistrationNotAllowed;
import com.masl.goofy_irc_be.exception.server.PublicKeyLookupFailed;
import com.masl.goofy_irc_be.properties.RegisterProperties;
import com.masl.goofy_irc_be.service.RegistrationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/register")
@Tag(name = "Registration", description = "Endpoints relating to the Registration of Users")
public class RegistrationEndpoint {
    private final RegisterProperties registerProperties;
    private final RegistrationService registrationService;

    public RegistrationEndpoint(RegisterProperties registerProperties, RegistrationService registrationService) {
        this.registerProperties = registerProperties;
        this.registrationService = registrationService;
    }

    // TODO: Rate Limit
    @PostMapping
    @PreAuthorize("hasRole('ROLE_OUTSIDE_ENTITY')")
    @IrcEndpoint(summary = "Attempt Registration", description = "To register, a registration code is required (It can be left blank IF the handle comes from a domain, which is in the `autoAllowDomains` list). The request needs to be signed with the keypair that wants to register.")
    public String register(@RequestBody(required = false) String code, @AuthenticationPrincipal GoofyAuthUser auth) throws RegistrationNotAllowed, InvalidRegisterCode, HandleAlreadyRegistered, RegistrationCodeAlreadyUsed, PublicKeyLookupFailed {
        if (!registerProperties.getRegistrationsAllowed())
            throw new RegistrationNotAllowed();
        if (code == null)
            code = "";

        registrationService.attemptRegistration(code, auth);
        return "Successfully registered user with handle: " + auth.getHandle();
    }

    @GetMapping("/status")
    @IrcEndpoint(summary = "Get the Registration Status (Are Registrations allowed? How do they get checked?)")
    public RegisterStatusDto registrationsAllowed() {
        return new RegisterStatusDto(
                registerProperties.getRegistrationsAllowed(),
                registerProperties.getCheckMethod(),
                registerProperties.getAllowedDomains()
        );
    }

    // TODO: Rate Limit?
    @GetMapping("/valid")
    @IrcEndpoint(summary = "Check if a Registration Code is Valid without using it")
    public boolean isRegistrationCodeValid(@RequestParam String code) {
        if (!registerProperties.getRegistrationsAllowed())
            return false;

        return registrationService.isCodeValid(code);
    }

    // TODO: Rate Limit
    @PostMapping("/request")
    @PreAuthorize("hasRole('ROLE_OUTSIDE_ENTITY')")
    @IrcEndpoint(summary = "Request a Registration Code.", description = "You will either be manually contacted by a person or an automated system might send you an email/etc.")
    public void requestRegistrationCode(@Valid @RequestBody RegistrationRequestDto requestDto, @AuthenticationPrincipal GoofyAuthUser auth) throws RegistrationNotAllowed {
        if (!registerProperties.getRegistrationsAllowed())
            throw new RegistrationNotAllowed();
        registrationService.submitRegistrationRequest(requestDto, auth.getHandle());
    }
}
