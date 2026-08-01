package com.masl.goofy_irc_be.exception.base.swagger;

import java.util.Map;

// This is the internal IrcErrorDto used to generate the Swagger Schema
public record IrcErrorDto(
        int errorCode,
        String message,
        Map<String,Object> details
) {}
