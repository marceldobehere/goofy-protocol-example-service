package com.masl.goofy_irc_be.exception.client.priv;

import com.masl.goofy_irc_be.exception.base.BaseClientIrcException;
import com.masl.goofy_irc_be.exception.base.swagger.IrcHttpErrorCode;
import com.masl.goofy_irc_be.exception.client.AllClientErrorCodes;

@IrcHttpErrorCode(errorCode = AllClientErrorCodes.PRIV_FRIEND_REQUEST_ALREADY_SENT)
public class FriendRequestAlreadySent extends BaseClientIrcException {
    public FriendRequestAlreadySent() {
        super("You already sent a friend request to this user!");
    }
}
