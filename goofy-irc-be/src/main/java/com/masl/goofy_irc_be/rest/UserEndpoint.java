//package com.masl.goofy_irc_be.rest;
//
//import com.masl.goofy_protocol_core.crypto.connected.GenericHandleCrypto;
//import com.masl.goofy_irc_be.auth.GoofyAuthUser;
//import com.masl.goofy_irc_be.config.ROLES;
//import com.masl.goofy_irc_be.crypto.IrcHandleCrypto;
//import com.masl.goofy_irc_be.dto.response.HandleLookupDto;
//import com.masl.goofy_irc_be.dto.response.MyUserInfoDto;
//import com.masl.goofy_irc_be.entity.IdentityStorageEntry;
//import com.masl.goofy_irc_be.entity.User;
//import com.masl.goofy_irc_be.exception.base.swagger.IrcEndpoint;
//import com.masl.goofy_irc_be.exception.server.PublicKeyLookupFailed;
//import com.masl.goofy_irc_be.properties.GeneralProperties;
//import com.masl.goofy_irc_be.repository.IdentityStorageEntryRepository;
//import com.masl.goofy_irc_be.repository.UserRepository;
//import io.swagger.v3.oas.annotations.tags.Tag;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.security.access.prepost.PreAuthorize;
//import org.springframework.security.core.annotation.AuthenticationPrincipal;
//import org.springframework.web.bind.annotation.*;
//
//// TODO: Write tests
//@RestController
//@RequestMapping("/api/user")
//@Tag(name = "User", description = "Endpoints relating to User Info")
//public class UserEndpoint {
//    private static final Logger log = LoggerFactory.getLogger(UserEndpoint.class);
//
//    private final UserRepository userRepository;
//    private final IdentityStorageEntryRepository identityRepository;
//    private final GeneralProperties generalProperties;
//    private final IrcHandleCrypto handleCrypto;
//
//    public UserEndpoint(UserRepository userRepository, IdentityStorageEntryRepository identityRepository, GeneralProperties generalProperties, IrcHandleCrypto handleCrypto) {
//        this.userRepository = userRepository;
//        this.identityRepository = identityRepository;
//        this.generalProperties = generalProperties;
//        this.handleCrypto = handleCrypto;
//    }
//
//    // Get My User Info (Handle, Public Key, Auth Role, ...)
//    @GetMapping("/info")
//    @PreAuthorize("hasRole('ROLE_REGISTERED_IDENTITY')")
//    @IrcEndpoint(summary = "Gets Information for the current User", description = "This Endpoint returns information about the current user/identity, including their handle, public key, and authentication role.")
//    public MyUserInfoDto myInfo(@AuthenticationPrincipal GoofyAuthUser auth) {
//        if (auth.getUser()) {
//            User user = userRepository.findByHandle(auth.getHandle());
//            return new MyUserInfoDto(auth.getHandle(), generalProperties.getDomain(), user.getPubSplitKey(), user.isAdmin() ? ROLES.AuthRoleEnumDto.ADMIN : ROLES.AuthRoleEnumDto.REGISTERED_USER, user.isRestricted());
//        } else if (auth.getIdentity()) {
//            IdentityStorageEntry entry = identityRepository.findByHandle(auth.getHandle());
//            return new MyUserInfoDto(auth.getHandle(), generalProperties.getDomain(), entry.getPubSplitKey(), ROLES.AuthRoleEnumDto.REGISTERED_IDENTITY, false);
//        }
//        return new MyUserInfoDto(auth.getHandle(), "", "", ROLES.AuthRoleEnumDto.OUTSIDE_ENTITY, false);
//    }
//
//    // Look Up User / Public Key Info based on Handle (Check if moved)
//    @GetMapping("/lookup/{handle}")
//    @IrcEndpoint(summary = "Looks up a User or Identity by Handle", description = "This Endpoint allows you to look up a user or identity by their handle")
//    public HandleLookupDto lookupUser(@PathVariable String handle) throws PublicKeyLookupFailed {
//        String strippedHandle = GenericHandleCrypto.stripPotentialDomainFromHandle(handle);
//
//        // TODO: Handle Moving User
//        // Check if its a user
//        User user = userRepository.findByHandle(strippedHandle);
//        if (user != null)
//            return new HandleLookupDto(strippedHandle, generalProperties.getDomain(), user.getPubSplitKey(), true);
//
//        // TODO: Handle Moving Identity
//        // Check if its a registered identity
//        IdentityStorageEntry entry = identityRepository.findByHandle(strippedHandle);
//        if (entry != null)
//            return new HandleLookupDto(strippedHandle, generalProperties.getDomain(), entry.getPubSplitKey(), true);
//
//        // Try getting public key from cache/external lookup
//        String localLookup = handleCrypto.getPublicSplitKeyFromHandle(handle);
//        if (localLookup != null) {
//            String optDomain = GenericHandleCrypto.getPotentialDomainFromHandle(handle);
//            // TODO: Maybe redirect or treat a bit differently, especially if we add extra info then we need some extra method for that
//            // but we'd also need to be careful about the registeredHere boolean
//            return new HandleLookupDto(strippedHandle, optDomain, localLookup, false);
//        }
//
//        // We don't know the handle, fail
//        throw new PublicKeyLookupFailed(handle);
//    }
//
//    // TODO: Potentially enforce having done an account-export within 7 days of trying to delete the account to avoid unwanted data loss
//    @DeleteMapping("/delete")
//    @PreAuthorize("hasRole('ROLE_REGISTERED_USER')")
//    @IrcEndpoint(summary = "Deletes the current User Account", description = "This Endpoint allows a user to delete their account. It will remove all associated data and identities. <br>This is a hard delete, do NOT expect to be able to recover your account without a backup afterwards!")
//    public void deleteUser(@AuthenticationPrincipal GoofyAuthUser auth) {
//        log.info("User {} requested account deletion", auth.getHandle());
//        userRepository.deleteByHandle(auth.getHandle());
//    }
//
//    // Update generic User Info
//    // - Custom Frontend URL
//
//    // Set/Update external Handle Information (for example the domain of the user)
//    // Would be good to have an extra table that has every user and identity ever registered (just the handle) + information if they have moved FIS domains
//    // Additionally support moving a singular identity handle -> shouldn't be too hard, just have to watch out in the request I guess
//    // Additionally add a custom Fis Exception everywhere where it's needed to indicate an Identity/Account was moved
//    // Move Account (Would be the same as update external handle information?)
//    // Would also need to affect all identities of the user
//
//    // Get Storage Details / Stats
//
//    // Get Complete Account Export (What about Tables / Buckets)
//    // Import FIS Data
//    // Should maybe be a two-step process like delete, because you'd replace all your old data
//    // For clients there'll be two options of importing an export (backup or when moving FIS), either direct import using the same keypair for registration
//    // - or decrypting and re-encrypting everything before importing it
//
//    // Deactivate Handle (Highly specific, needs more thought put into it)
//
//    // You should also be able to move identities to a different identity, e.g. if you change your handle (because maybe you changed to a post quantum cryptography algo and now have a new keypair/identity)
//}
