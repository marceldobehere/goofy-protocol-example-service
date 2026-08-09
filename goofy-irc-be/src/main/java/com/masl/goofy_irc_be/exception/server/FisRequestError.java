package com.masl.goofy_irc_be.exception.server;

import com.masl.goofy_irc_be.exception.base.BaseServerIrcException;
import com.masl.goofy_irc_be.exception.base.swagger.IrcHttpErrorCode;

import java.util.Map;

@IrcHttpErrorCode(errorCode = AllServerErrorCodes.FIS_REQUEST_ERROR, detailFields = {"errorStr"})
public class FisRequestError extends BaseServerIrcException {
    public FisRequestError(String errorStr) {
        super("Sending Request to FIS returned error: " + errorStr, Map.of("errorStr", errorStr));
    }
}
