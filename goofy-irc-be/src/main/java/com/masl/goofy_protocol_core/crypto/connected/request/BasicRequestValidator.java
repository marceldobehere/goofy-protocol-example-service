package com.masl.goofy_protocol_core.crypto.connected.request;

import java.time.Instant;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;


public class BasicRequestValidator implements SignedRequestValidator {
    public static final int DEF_MAX_VALIDITY_GRACE_PERIOD = 5;
    public static final int DEF_MAX_VALIDITY_FUTURE = 60 * 60;

    private static final int DEF_MAX_USED_IDS = 10_000;
    private final Set<Long> usedIds = new HashSet<>();

    @Override
    public boolean isUniqueIdValid(long uniqueId) {
        synchronized (usedIds) {
            return !usedIds.contains(uniqueId);
        }
    }

    @Override
    public boolean invalidateUniqueId(long uniqueId) {
        synchronized (usedIds) {
            boolean added = usedIds.add(uniqueId);

            // Just remove a "random" element, to keep the size contained
            if (added && usedIds.size() > DEF_MAX_USED_IDS) {
                Iterator<Long> it = usedIds.iterator();
                it.next();
                it.remove();
            }
            return added;
        }
    }

    @Override
    public boolean isValidUntilValid(Instant validUntil) {
        return validUntil.isAfter(Instant.now().minusSeconds(DEF_MAX_VALIDITY_GRACE_PERIOD)) &&
                validUntil.isBefore(Instant.now().plusSeconds(DEF_MAX_VALIDITY_FUTURE));
    }
}
