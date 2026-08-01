package com.masl.goofy_irc_be.exception.client;

import com.masl.goofy_irc_be.exception.base.BaseClientIrcException;
import com.masl.goofy_irc_be.exception.base.swagger.IrcHttpErrorCode;

import java.util.Map;

@IrcHttpErrorCode(errorCode = AllClientErrorCodes.REGISTRATION_CODE_ALREADY_USED, detailFields = {"code"})
public class RegistrationCodeAlreadyUsed extends BaseClientIrcException {
    public RegistrationCodeAlreadyUsed(String code) {
        super("Code already used: " + code, Map.of("code", code));
    }
}
