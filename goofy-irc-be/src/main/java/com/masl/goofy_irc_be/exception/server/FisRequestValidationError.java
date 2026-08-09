package com.masl.goofy_irc_be.exception.server;

import com.masl.goofy_irc_be.exception.base.BaseServerIrcException;
import com.masl.goofy_irc_be.exception.base.swagger.IrcHttpErrorCode;

import java.util.Map;

@IrcHttpErrorCode(errorCode = AllServerErrorCodes.FIS_REQUEST_VALIDATION_ERROR, detailFields = {"errorStr"})
public class FisRequestValidationError extends BaseServerIrcException {
    public FisRequestValidationError(String errorStr) {
        super("Sending Request to FIS returned error: " + errorStr, Map.of("errorStr", errorStr));
    }
}
