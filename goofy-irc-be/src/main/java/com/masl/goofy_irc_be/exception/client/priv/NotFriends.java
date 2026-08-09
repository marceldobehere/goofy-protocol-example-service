package com.masl.goofy_irc_be.exception.client.priv;

import com.masl.goofy_irc_be.exception.base.BaseClientIrcException;
import com.masl.goofy_irc_be.exception.base.swagger.IrcHttpErrorCode;
import com.masl.goofy_irc_be.exception.client.AllClientErrorCodes;

import java.util.Map;

@IrcHttpErrorCode(errorCode = AllClientErrorCodes.PRIV_NOT_FRIENDS, detailFields = {"handle"})
public class NotFriends extends BaseClientIrcException {
    public NotFriends(String handle) {
        super("You are not friends with: " + handle, Map.of("handle", handle));
    }
}
