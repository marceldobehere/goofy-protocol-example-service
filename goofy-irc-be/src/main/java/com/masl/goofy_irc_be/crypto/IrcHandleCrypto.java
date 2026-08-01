package com.masl.goofy_irc_be.crypto;

import com.masl.goofy_protocol_core.crypto.connected.HandleCrypto;
import org.springframework.stereotype.Component;

@Component
public class IrcHandleCrypto extends HandleCrypto {
    public IrcHandleCrypto(HandleHelper handleHelper) {
        super(handleHelper);
    }
}
