package com.masl.goofy_irc_be.rest.admin;

import com.masl.goofy_irc_be.auth.GoofyAuthUser;
import com.masl.goofy_irc_be.dto.response.RegisterRequestEntryDto;
import com.masl.goofy_irc_be.dto.response.RegistrationCodeDto;
import com.masl.goofy_irc_be.entity.RegistrationCode;
import com.masl.goofy_irc_be.entity.User;
import com.masl.goofy_irc_be.exception.base.swagger.IrcEndpoint;
import com.masl.goofy_irc_be.exception.client.*;
import com.masl.goofy_irc_be.repository.UserRepository;
import com.masl.goofy_irc_be.service.RegistrationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// TODO: Test
@RestController
@RequestMapping("/irc-api/admin/register")
@Tag(name = "Registration (Admin)", description = "Admin Endpoints relating to the Registration of Users")
public class AdminRegistrationEndpoint {
    private final RegistrationService registrationService;
    private final UserRepository userRepository;

    public AdminRegistrationEndpoint(RegistrationService registrationService, UserRepository userRepository) {
        this.registrationService = registrationService;
        this.userRepository = userRepository;
    }

    // Get all Registration Requests
    @GetMapping("/request")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @IrcEndpoint(summary = "Get All Registration Requests", description = "This Endpoint allows an admin to retrieve all registration requests submitted to the IRC. <br> The response will be a list of registration request entries, each containing details such as ID, message, general contact information, optional email, creation timestamp, and resolution timestamp.")
    public List<RegisterRequestEntryDto> getAllRegistrations() {
        return registrationService.getAllRequests().stream()
                .map(req -> new RegisterRequestEntryDto(
                        req.getId(),
                        req.getMessage(),
                        req.getGeneralContact(),
                        req.getOptEmail(),
                        req.getCreatedAt(),
                        req.getResolvedAt()
                )).toList();
    }

    // Get all unresolved Registration Requests
    @GetMapping("/request/open")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @IrcEndpoint(summary = "Get All Unresolved Registration Requests", description = "This Endpoint allows an admin to retrieve all unresolved registration requests submitted to the IRC. <br> The response will be a list of registration request entries that have not been marked as resolved, each containing details such as ID, message, general contact information, optional email, creation timestamp, and resolution timestamp (which will be null for unresolved requests).")
    public List<RegisterRequestEntryDto> getAllUnresolvedRegistrations() {
        return registrationService.getAllUnresolvedRequests().stream()
                .map(req -> new RegisterRequestEntryDto(
                        req.getId(),
                        req.getMessage(),
                        req.getGeneralContact(),
                        req.getOptEmail(),
                        req.getCreatedAt(),
                        req.getResolvedAt()
                )).toList();
    }

    // Get Registration Request
    @GetMapping("/request/{id}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @IrcEndpoint(summary = "Get a Specific Registration Request", description = "This Endpoint allows an admin to retrieve a specific registration request by its ID.")
    public RegisterRequestEntryDto getRegisterRequest(@PathVariable Long id) throws GenericNotFound {
        var req = registrationService.getRequestById(id);
        return new RegisterRequestEntryDto(
                req.getId(),
                req.getMessage(),
                req.getGeneralContact(),
                req.getOptEmail(),
                req.getCreatedAt(),
                req.getResolvedAt()
        );
    }

    // Resolve Registration
    @PostMapping("/request/{id}/resolve")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @IrcEndpoint(summary = "Resolve a Registration Request", description = "This Endpoint allows an admin to mark a specific registration request as resolved or unresolved. <br> The request body should contain a boolean value indicating the desired resolved status (true for resolved, false for unresolved).")
    public void resolveReport(@PathVariable Long id, @Valid @RequestBody Boolean resolved) throws GenericNotFound {
        registrationService.setResolvedStatus(id, resolved);
    }

    // Delete Registration
    @DeleteMapping("/request/{id}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @IrcEndpoint(summary = "Delete a Registration Request", description = "This Endpoint allows an admin to delete a specific registration request by its ID.")
    public void deleteRegistrationRequest(@PathVariable Long id) {
        registrationService.deleteRegistrationRequest(id);
    }

    // Get Unused Registration Codes
    @GetMapping("/code/unused")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @IrcEndpoint(summary = "Get All Unused Registration Codes", description = "This Endpoint allows an admin to retrieve all unused registration codes.")
    public List<RegistrationCodeDto> getAllUnusedRegistrationCodes() {
        return registrationService.getAllUnusedCodes().stream()
                .map(code -> new RegistrationCodeDto(
                        code.getCode(),
                        code.getAdmin(),
                        code.getCreatedBy() == null ? null : code.getCreatedBy().getHandle(),
                        code.getCreatedAt(),
                        code.getUsedBy() == null ? null : code.getUsedBy().getHandle(),
                        code.getUsedAt()
                )).toList();
    }

    // Get Used Registration Codes
    @GetMapping("/code/used")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @IrcEndpoint(summary = "Get All Used Registration Codes", description = "This Endpoint allows an admin to retrieve all used registration codes.")
    public List<RegistrationCodeDto> getAllUsedRegistrationCodes() {
        return registrationService.getAllUsedCodes().stream()
                .map(code -> new RegistrationCodeDto(
                        code.getCode(),
                        code.getAdmin(),
                        code.getCreatedBy() == null ? null : code.getCreatedBy().getHandle(),
                        code.getCreatedAt(),
                        code.getUsedBy() == null ? null : code.getUsedBy().getHandle(),
                        code.getUsedAt()
                )).toList();
    }

    // Create Registration Code
    @PostMapping("/code")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @IrcEndpoint(summary = "Create a Registration Code", description = "This Endpoint allows an admin to create a new registration code.")
    public RegistrationCodeDto createRegistrationCode(@AuthenticationPrincipal GoofyAuthUser auth, @Valid @RequestBody Boolean isAdmin) {
        User user = userRepository.findByHandle(auth.getHandle());
        RegistrationCode code = registrationService.createNewRegistrationCode(user, isAdmin);
        return new RegistrationCodeDto(
                code.getCode(),
                code.getAdmin(),
                code.getCreatedBy() == null ? null : code.getCreatedBy().getHandle(),
                code.getCreatedAt(),
                code.getUsedBy() == null ? null : code.getUsedBy().getHandle(),
                code.getUsedAt()
        );
    }

    // Delete Registration Code
    @DeleteMapping("/code/{code}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @IrcEndpoint(summary = "Delete a Registration Code", description = "This Endpoint allows an admin to delete a specific registration code.")
    public void deleteRegistrationCode(@PathVariable String code) {
        registrationService.deleteRegistrationCode(code);
    }
}
