package com.masl.goofy_protocol_core.crypto.connected.request;

import java.time.Instant;

public interface SignedRequestValidator {
    boolean isUniqueIdValid(long uniqueId);
    boolean isValidUntilValid(Instant validUntil);

    boolean invalidateUniqueId(long uniqueId);
}
