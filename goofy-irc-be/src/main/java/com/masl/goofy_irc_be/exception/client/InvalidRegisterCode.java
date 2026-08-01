package com.masl.goofy_irc_be.exception.client;

import com.masl.goofy_irc_be.exception.base.BaseClientIrcException;
import com.masl.goofy_irc_be.exception.base.swagger.IrcHttpErrorCode;

import java.util.Map;

@IrcHttpErrorCode(errorCode = AllClientErrorCodes.INVALID_REGISTER_CODE, detailFields = {"code"})
public class InvalidRegisterCode extends BaseClientIrcException {
    public InvalidRegisterCode(String code) {
        super("Invalid register code: " + code, Map.of("code", code));
    }
}
