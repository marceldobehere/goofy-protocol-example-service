package com.masl.goofy_irc_be.exception.base;

import com.masl.goofy_irc_be.exception.base.swagger.IrcHttpErrorCode;

import java.util.Map;

@IrcHttpErrorCode(httpStatus = 500)
public class BaseServerIrcException extends BaseClassIrcException {
    public BaseServerIrcException(String message, Map<String, Object> errorDetails) {
        super(message, errorDetails);
    }

    public BaseServerIrcException(String message) {
        super(message);
    }
}
