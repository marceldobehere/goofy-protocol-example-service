package com.masl.goofy_irc_be.exception.base;

import com.masl.goofy_irc_be.exception.base.swagger.IrcHttpErrorCode;

import java.util.Map;

@IrcHttpErrorCode(httpStatus = 400)
public class BaseClientIrcException extends BaseClassIrcException {
    public BaseClientIrcException(String message, Map<String, Object> errorDetails) {
        super(message, errorDetails);
    }

    public BaseClientIrcException(String message) {
        super(message);
    }
}
