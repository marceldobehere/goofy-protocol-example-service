package com.masl.goofy_irc_be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IrcHandleLookupDto {
    private String handle;
    private String handleDomain;
    private String pubKey;
    private boolean registeredHere;
}
