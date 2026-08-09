package com.masl.goofy_irc_be.exception.client.priv;

import com.masl.goofy_irc_be.exception.base.BaseClientIrcException;
import com.masl.goofy_irc_be.exception.base.swagger.IrcHttpErrorCode;
import com.masl.goofy_irc_be.exception.client.AllClientErrorCodes;

@IrcHttpErrorCode(errorCode = AllClientErrorCodes.PRIV_FRIEND_REQUESTS_NOT_ALLOWED)
public class FriendRequestsNotAllowed extends BaseClientIrcException {
    public FriendRequestsNotAllowed() {
        super("User does not allow friend requests (in general / for this handle)");
    }
}
