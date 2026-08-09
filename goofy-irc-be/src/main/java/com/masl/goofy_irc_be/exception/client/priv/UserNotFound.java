package com.masl.goofy_irc_be.exception.client.priv;

import com.masl.goofy_irc_be.exception.base.BaseClientIrcException;
import com.masl.goofy_irc_be.exception.base.swagger.IrcHttpErrorCode;
import com.masl.goofy_irc_be.exception.client.AllClientErrorCodes;

import java.util.Map;

@IrcHttpErrorCode(httpStatus = 404, errorCode = AllClientErrorCodes.PRIV_USER_NOT_FOUND, detailFields = {"handle"})
public class UserNotFound extends BaseClientIrcException {
    public UserNotFound(String handle) {
        super("User with handle not found: " + handle, Map.of("handle", handle));
    }
}
