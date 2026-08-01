package com.masl.goofy_irc_be.exception.server;

import com.masl.goofy_irc_be.exception.base.BaseServerIrcException;
import com.masl.goofy_irc_be.exception.base.swagger.IrcHttpErrorCode;

import java.util.Map;

@IrcHttpErrorCode(errorCode = AllServerErrorCodes.PUBLIC_KEY_LOOKUP_FAILED, detailFields = {"handle"})
public class PublicKeyLookupFailed extends BaseServerIrcException {
    public PublicKeyLookupFailed(String handle) {
        super("Public key lookup failed for handle: " + handle, Map.of("handle", handle));
    }
}
