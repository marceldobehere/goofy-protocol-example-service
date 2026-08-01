package com.masl.goofy_irc_be.exception.client;

import com.masl.goofy_irc_be.exception.base.BaseClientIrcException;
import com.masl.goofy_irc_be.exception.base.swagger.IrcHttpErrorCode;

import java.util.Map;

@IrcHttpErrorCode(errorCode = AllClientErrorCodes.HANDLE_ALREADY_REGISTERED, detailFields = {"handle"})
public class HandleAlreadyRegistered extends BaseClientIrcException {
    public HandleAlreadyRegistered(String handle) {
        super("Handle already registered: " + handle, Map.of("handle", handle));
    }
}
