package com.masl.goofy_irc_be.exception.client.priv;

import com.masl.goofy_irc_be.exception.base.BaseClientIrcException;
import com.masl.goofy_irc_be.exception.base.swagger.IrcHttpErrorCode;
import com.masl.goofy_irc_be.exception.client.AllClientErrorCodes;

@IrcHttpErrorCode(errorCode = AllClientErrorCodes.PRIV_ALREADY_FRIENDS)
public class AlreadyFriends extends BaseClientIrcException {
    public AlreadyFriends() {
        super("You are already friends with this user!");
    }
}
