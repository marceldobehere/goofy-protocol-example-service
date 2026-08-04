package com.masl.goofy_irc_be.exception.client.room;

import com.masl.goofy_irc_be.exception.base.BaseClientIrcException;
import com.masl.goofy_irc_be.exception.base.swagger.IrcHttpErrorCode;
import com.masl.goofy_irc_be.exception.client.AllClientErrorCodes;

import java.util.Map;

@IrcHttpErrorCode(errorCode = AllClientErrorCodes.ROOM_INVALID_PASSWORD_HASH, detailFields = {"reason"})
public class RoomInvalidPasswordHash extends BaseClientIrcException {
    public RoomInvalidPasswordHash(String reason) {
        super("Password invalid: " + reason, Map.of("reason", reason));
    }
}
