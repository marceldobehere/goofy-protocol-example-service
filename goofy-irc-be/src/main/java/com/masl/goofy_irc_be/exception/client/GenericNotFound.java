package com.masl.goofy_irc_be.exception.client;

import com.masl.goofy_irc_be.exception.base.BaseClientIrcException;
import com.masl.goofy_irc_be.exception.base.swagger.IrcHttpErrorCode;

import java.util.Map;

@IrcHttpErrorCode(httpStatus = 404, errorCode = AllClientErrorCodes.GENERIC_NOT_FOUND, detailFields = {"id"}, description = "The provided ID does not exist.")
public class GenericNotFound extends BaseClientIrcException {
    public GenericNotFound(Long id) {
        super("ID not found: " + id, Map.of("id", id));
    }
}
