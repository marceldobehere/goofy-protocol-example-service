package com.masl.goofy_irc_be.exception.client;

import com.masl.goofy_irc_be.exception.base.BaseClientIrcException;
import com.masl.goofy_irc_be.exception.base.swagger.IrcHttpErrorCode;

@IrcHttpErrorCode(httpStatus = 405, errorCode = AllClientErrorCodes.REGISTRATION_NOT_ALLOWED)
public class RegistrationNotAllowed extends BaseClientIrcException {
    public RegistrationNotAllowed() {
        super("Registrations are not allowed");
    }
}
