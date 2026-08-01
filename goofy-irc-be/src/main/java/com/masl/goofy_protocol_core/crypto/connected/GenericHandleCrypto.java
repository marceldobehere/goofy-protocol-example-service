package com.masl.goofy_protocol_core.crypto.connected;

import com.masl.goofy_protocol_core.crypto.isolated.asymm.AsymmCrypto;

import java.util.regex.Pattern;

public interface GenericHandleCrypto {
    String DEF_DOMAIN_SEPARATOR = "@";

    // Verification
    boolean verifyKeyAndHandle(String pubSplitKey, String handle);
    boolean verifyKeyAndHandle(AsymmCrypto.AsymmPubKeyPair pubSplitKey, String handle);

    // Keypair to Handle Derivation
    String deriveHandle(String pubSplitKey);
    String deriveHandle(AsymmCrypto.AsymmPubKeyPair pubSplitKey);

    // Handle to Keypair Lookup
    // NOTE: The handle might have a domain attached (See Spec)
    String getPublicSplitKeyFromHandle(String handle);


    // Handle & Domain Separation
    static String stripPotentialDomainFromHandle(String handle) {
        return handle.split(Pattern.quote(DEF_DOMAIN_SEPARATOR))[0];
    }
    static String getPotentialDomainFromHandle(String handle) {
        String[] split = handle.split(Pattern.quote(DEF_DOMAIN_SEPARATOR));
        if (split.length > 1)
            return split[1];
        return null;
    }
}
