package com.masl.goofy_irc_be.auth;

import com.masl.goofy_protocol_core.crypto.connected.request.SignedRequest;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.security.Principal;

@NoArgsConstructor
@AllArgsConstructor
@Getter @Setter
public class GoofyAuthUser implements Principal {
    private String handle;

    private Boolean user;

    private Boolean admin;

    private SignedRequest signedRequest;

    @Override
    public String getName() {
        return handle;
    }
}
