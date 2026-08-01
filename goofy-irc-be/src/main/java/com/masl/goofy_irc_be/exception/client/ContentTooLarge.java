package com.masl.goofy_irc_be.exception.client;

import com.masl.goofy_irc_be.exception.base.BaseClientIrcException;
import com.masl.goofy_irc_be.exception.base.swagger.IrcHttpErrorCode;

@IrcHttpErrorCode(httpStatus = 413, errorCode = AllClientErrorCodes.CONTENT_TOO_LARGE, description = "This means that the content/payload sent to the server is too large.")
public class ContentTooLarge extends BaseClientIrcException {
    public ContentTooLarge() {
        super("Content/Payload too large");
    }
}
