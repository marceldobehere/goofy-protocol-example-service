package com.masl.goofy_irc_be.rest;

import com.masl.goofy_irc_be.auth.GoofyAuthUser;
import com.masl.goofy_irc_be.config.ROLES;
import com.masl.goofy_irc_be.dto.response.IrcHandleLookupDto;
import com.masl.goofy_irc_be.dto.both.MyUserInfoDto;
import com.masl.goofy_irc_be.entity.CachedKeyHandleEntry;
import com.masl.goofy_irc_be.entity.User;
import com.masl.goofy_irc_be.exception.base.swagger.IrcEndpoint;
import com.masl.goofy_irc_be.exception.server.PublicKeyLookupFailed;
import com.masl.goofy_irc_be.properties.GeneralProperties;
import com.masl.goofy_irc_be.repository.CachedKeyHandleRepository;
import com.masl.goofy_irc_be.repository.UserRepository;
import com.masl.goofy_protocol_core.crypto.connected.GenericHandleCrypto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

// TODO: Write tests
@RestController
@RequestMapping("/api/user")
@Tag(name = "User", description = "Endpoints relating to User Info")
public class UserEndpoint {
    private static final Logger log = LoggerFactory.getLogger(UserEndpoint.class);

    private final UserRepository userRepository;
    private final GeneralProperties generalProperties;
    private final CachedKeyHandleRepository cachedKeyHandleRepository;

    public UserEndpoint(UserRepository userRepository, GeneralProperties generalProperties, CachedKeyHandleRepository cachedKeyHandleRepository) {
        this.userRepository = userRepository;
        this.generalProperties = generalProperties;
        this.cachedKeyHandleRepository = cachedKeyHandleRepository;
    }

    // Get My User Info (Handle, Public Key, Auth Role, ...)
    @GetMapping("/info")
    @PreAuthorize("hasRole('ROLE_REGISTERED_USER')")
    @IrcEndpoint(summary = "Gets Information for the current User", description = "This Endpoint returns information about the current user/identity, including their handle, public key, and authentication role.")
    public MyUserInfoDto myInfo(@AuthenticationPrincipal GoofyAuthUser auth) {
        User user = userRepository.findByHandle(auth.getHandle());
        return new MyUserInfoDto(auth.getHandle(), generalProperties.getDomain(), user.getPubSplitKey(), user.isAdmin() ? ROLES.AuthRoleEnumDto.ADMIN : ROLES.AuthRoleEnumDto.REGISTERED_USER, user.getFriendRequestTablePath(), user.getReceivedDmsTablePath(), user.getFriendListTablePath(), user.getFriendRequestSetting());
    }

    // Update My User Info
    @PutMapping("/info")
    @PreAuthorize("hasRole('ROLE_REGISTERED_USER')")
    @IrcEndpoint(summary = "Updates Information for the current User", description = "You can update: `friendRequestTablePath`, `friendListTablePath` and `receivedDmsTablePath`. The format is `identityHandle@serviceUuid@tableUuid`")
    public MyUserInfoDto updateMyInfo(@AuthenticationPrincipal GoofyAuthUser auth, @Valid @RequestBody MyUserInfoDto updateDto) {
        User user = userRepository.findByHandle(auth.getHandle());
        user.setFriendRequestTablePath(updateDto.getFriendRequestTablePath());
        user.setReceivedDmsTablePath(updateDto.getReceivedDmsTablePath());
        user.setFriendListTablePath(updateDto.getFriendListTablePath());
        userRepository.save(user);
        return new MyUserInfoDto(auth.getHandle(), generalProperties.getDomain(), user.getPubSplitKey(), user.isAdmin() ? ROLES.AuthRoleEnumDto.ADMIN : ROLES.AuthRoleEnumDto.REGISTERED_USER, user.getFriendRequestTablePath(), user.getReceivedDmsTablePath(), user.getFriendListTablePath(), user.getFriendRequestSetting());
    }

    @DeleteMapping("/delete")
    @PreAuthorize("hasRole('ROLE_REGISTERED_USER')")
    @IrcEndpoint(summary = "Deletes the current User Account", description = "This Endpoint allows a user to delete their account. It will remove all associated data and identities. <br>This is a hard delete, do NOT expect to be able to recover your account without a backup afterwards!")
    public void deleteUser(@AuthenticationPrincipal GoofyAuthUser auth) {
        log.info("User {} requested account deletion", auth.getHandle());
        userRepository.deleteByHandle(auth.getHandle());
    }

    // Look Up User / Public Key Info based on Handle (Check if moved)
    @GetMapping("/lookup/{handle}")
    @IrcEndpoint(summary = "Looks up a User or Identity by Handle", description = "This Endpoint allows you to look up a user or identity by their handle")
    public IrcHandleLookupDto lookupUser(@PathVariable String handle) throws PublicKeyLookupFailed {
        String strippedHandle = GenericHandleCrypto.stripPotentialDomainFromHandle(handle);

        // Check if we got it cached
        // TODO: Check domain again?
        CachedKeyHandleEntry entry = cachedKeyHandleRepository.findByHandle(strippedHandle);
        if (entry == null || entry.getHandleDomain() == null) {
            // TODO: potentially try looking up from the domains we already know
            throw new PublicKeyLookupFailed(handle);
        }

        // TODO: Handle Moving User
        // Check if its a user
        User user = userRepository.findByHandle(strippedHandle);
        if (user != null)
            return new IrcHandleLookupDto(strippedHandle, entry.getHandleDomain(), user.getPubSplitKey(), true);

        // We know the user but they are not registered here
        return new IrcHandleLookupDto(strippedHandle, entry.getHandleDomain(), entry.getPubSplitKey(), false);
    }
}
