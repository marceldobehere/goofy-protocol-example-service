package com.masl.goofy_irc_be.auth;

import com.masl.goofy_protocol_core.crypto.connected.request.SignedRequest;
import com.masl.goofy_irc_be.config.ROLES;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class GoofyAuth implements Authentication {
    private final List<GrantedAuthority> authorities;
    private final SignedRequest signedRequest;
    private final boolean isIdentity;
    private final boolean isAdmin;
    private final boolean isUser;
    private final boolean isRestricted;
    private boolean isAuthenticated;

    public GoofyAuth() {
        signedRequest = null;
        isIdentity = false;
        isAdmin = false;
        isUser = false;
        isRestricted = false;
        authorities = new ArrayList<>();
        isAuthenticated = false;
    }

    public GoofyAuth(SignedRequest signedRequest, boolean isIdentity, boolean isUser, boolean isAdmin, boolean isRestricted) {
        if (signedRequest == null)
            throw new IllegalArgumentException("SignedRequest cannot be null for authenticated GoofyAuth");

        this.signedRequest = signedRequest;
        this.isAdmin = isAdmin;
        this.isUser = isUser;
        this.isRestricted = isRestricted;
        this.isIdentity = isIdentity;
        isAuthenticated = true;
        authorities = new ArrayList<>();

        authorities.add(new SimpleGrantedAuthority("ROLE_" + ROLES.OUTSIDE_ENTITY));
        if (isIdentity)
            authorities.add(new SimpleGrantedAuthority("ROLE_" + ROLES.REGISTERED_IDENTITY));
        if (isUser)
            authorities.add(new SimpleGrantedAuthority("ROLE_" + ROLES.REGISTERED_USER));
        if (isAdmin)
            authorities.add(new SimpleGrantedAuthority("ROLE_" + ROLES.ADMIN));
        if (isRestricted)
            authorities.add(new SimpleGrantedAuthority("ROLE_" + ROLES.RESTRICTED));
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return new ArrayList<>(authorities);
    }

    @Override
    public @Nullable Object getCredentials() {
        return null;
    }

    @Override
    public @Nullable Object getDetails() {
        return null;
    }

    @Override
    public @Nullable Object getPrincipal() {
        if (signedRequest == null)
            return null;

        return new GoofyAuthUser(signedRequest.handle(), isIdentity, isUser, isAdmin, isRestricted, signedRequest);
    }

    @Override
    public boolean isAuthenticated() {
        return isAuthenticated;
    }

    @Override
    public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
        this.isAuthenticated = isAuthenticated;
    }

    @Override
    public String getName() {
        if (signedRequest == null)
            return "GUEST";
        return signedRequest.handle();
    }
}
