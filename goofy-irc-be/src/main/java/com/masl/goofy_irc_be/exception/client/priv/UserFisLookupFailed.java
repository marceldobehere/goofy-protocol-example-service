package com.masl.goofy_irc_be.exception.client.priv;

import com.masl.goofy_irc_be.exception.base.BaseClientIrcException;
import com.masl.goofy_irc_be.exception.base.swagger.IrcHttpErrorCode;
import com.masl.goofy_irc_be.exception.client.AllClientErrorCodes;

import java.util.Map;

@IrcHttpErrorCode(httpStatus = 404, errorCode = AllClientErrorCodes.PRIV_USER_FIS_LOOKUP_FAILED, detailFields = {"handle"})
public class UserFisLookupFailed extends BaseClientIrcException {
    public UserFisLookupFailed(String handle) {
        super("FIS Lookup failed for: " + handle, Map.of("handle", handle));
    }
}
