package com.masl.goofy_irc_be.exception.client;

import com.masl.goofy_protocol_core.crypto.connected.request.SignedRequest;
import com.masl.goofy_irc_be.exception.base.BaseClientIrcException;
import com.masl.goofy_irc_be.exception.base.swagger.IrcHttpErrorCode;

import java.util.Map;

@IrcHttpErrorCode(errorCode = AllClientErrorCodes.INVALID_SIGNATURE, detailFields = {"validity"}, description = "This means that a signature passed is for some reason invalid. The \"validity\" Field contains the exact reason. (See SignedRequest.SignedRequestValidity)")
public class InvalidSignature extends BaseClientIrcException {
    public InvalidSignature(SignedRequest.SignedRequestValidity signedRequestValidity) {
        super("Invalid signature: " + signedRequestValidity, Map.of("validity", signedRequestValidity.name()));
    }
}
