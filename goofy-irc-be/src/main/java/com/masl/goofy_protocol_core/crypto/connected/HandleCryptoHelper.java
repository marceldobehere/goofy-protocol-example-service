package com.masl.goofy_protocol_core.crypto.connected;

import java.util.List;
import java.util.Map;

public interface HandleCryptoHelper {
    // Word List (Should use the word list defined in the protocol)
    List<String> loadWordList();

    // General Cache
    Map<String, String> loadPersistedKeyToHandleMapCache();
    boolean addPersistedKeyToHandleMapping(String pubSplitKey, String handle);

    // All Known / Registered / Important Users
    Map<String, String> loadUserKeyToHandleMap();

    // Lookup Public Split Key for a handle (which is unknown to the cache, service might look up by asking different servers or return null if no mapping was found)
    // NOTE: The handle might have a domain attached (See Spec)
    String lookupPubSplitKeyForHandleExternally(String handle);
}
