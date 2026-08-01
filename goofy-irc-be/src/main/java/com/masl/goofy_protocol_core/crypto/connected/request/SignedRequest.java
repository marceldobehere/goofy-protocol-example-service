package com.masl.goofy_protocol_core.crypto.connected.request;

import com.masl.goofy_protocol_core.crypto.connected.GenericHandleCrypto;
import com.masl.goofy_protocol_core.crypto.exceptions.PubSplitKeyNotFound;
import com.masl.goofy_protocol_core.crypto.isolated.SecretUtils;
import com.masl.goofy_protocol_core.crypto.isolated.asymm.AsymmCrypto;
import com.masl.goofy_protocol_core.crypto.isolated.asymm.GlobAsymmCrypto;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static com.masl.goofy_protocol_core.crypto.connected.GenericHandleCrypto.DEF_DOMAIN_SEPARATOR;

public record SignedRequest(
        String pubSplitKey,
        String handle, // does not contain the domain part
        String signature,
        long uniqueId,
        Instant validUntil,
        String method,
        byte[] pathHash,
        byte[] bodyHash
) {
    public static final byte[] EMPTY_BODY_VAL = new byte[] { 123 };
    public static final int DEF_HASH_SIZE = 256 / 8;
    public static final String DEF_SEPARATOR = "#";
    public static final int DEF_MAX_VALIDITY = 60;

    private static final GlobAsymmCrypto asymmCrypto = new GlobAsymmCrypto();

    public enum SignedRequestValidity {
        VALID,
        MISSING_PARTS,
        INVALID_HASH_SIZE,
        INVALID_TIME,
        INVALID_ID,
        HANDLE_MISMATCH,
        INVALID_SIGNATURE
    }

    public SignedRequestValidity isValid(GenericHandleCrypto handleCrypto, SignedRequestValidator validator) {
        if (handleCrypto == null || validator == null)
            return SignedRequestValidity.MISSING_PARTS;

        // Check general validity
        if (pubSplitKey == null || handle == null || signature == null || validUntil == null || method == null || pathHash == null || bodyHash == null)
            return SignedRequestValidity.MISSING_PARTS;
        if (pathHash.length != DEF_HASH_SIZE || bodyHash.length != DEF_HASH_SIZE)
            return SignedRequestValidity.INVALID_HASH_SIZE;

        // Check time based and unique validity
        if (!validator.isValidUntilValid(validUntil))
            return SignedRequestValidity.INVALID_TIME;
        if (!validator.isUniqueIdValid(uniqueId))
            return SignedRequestValidity.INVALID_ID;

        // Check Public Key
        asymmCrypto.checkPublicSplitKey(pubSplitKey);

        // Check Handle against Public Key
        if (!handleCrypto.verifyKeyAndHandle(pubSplitKey, handle))
            return SignedRequestValidity.HANDLE_MISMATCH;

        // Check Signature
        String baseObj =
                method + DEF_SEPARATOR +
                uniqueId + DEF_SEPARATOR +
                validUntil.toEpochMilli() + DEF_SEPARATOR +
                Base64.getUrlEncoder().encodeToString(pathHash) + DEF_SEPARATOR +
                Base64.getUrlEncoder().encodeToString(bodyHash);
        try {
            if (!asymmCrypto.verifyStr(baseObj, signature, pubSplitKey))
                return SignedRequestValidity.INVALID_SIGNATURE;
        } catch (Exception e) {
            return SignedRequestValidity.INVALID_SIGNATURE;
        }

        return SignedRequestValidity.VALID;
    }

    public static SignedRequest fromParts(AsymmCrypto.AsymmFullKeyPair keypair, String method, String path, byte[] body, GenericHandleCrypto handleCrypto) {
        if (body == null || body.length == 0)
            body = EMPTY_BODY_VAL;

        // Check
        if (method == null || path == null)
            throw new IllegalArgumentException("Method and Path must be provided");
        if (handleCrypto == null)
            throw new IllegalArgumentException("HandleCrypto must be provided");

        // Calculate Hash
        byte[] pathHash = SecretUtils.sha256(path);
        byte[] bodyHash = SecretUtils.sha256(body);

        // Get Id and Time
        long uniqueId = new SecureRandom().nextLong();
        Instant validUntil = Instant.now().plusSeconds(DEF_MAX_VALIDITY);


        // Create Signature
        String baseObj =
                method + DEF_SEPARATOR +
                uniqueId + DEF_SEPARATOR +
                validUntil.toEpochMilli() + DEF_SEPARATOR +
                Base64.getUrlEncoder().encodeToString(pathHash) + DEF_SEPARATOR +
                Base64.getUrlEncoder().encodeToString(bodyHash);
        String sig = asymmCrypto.signStr(baseObj, keypair.priv().serialize());

        // Create Signed request
        return new SignedRequest(
                keypair.pub().serialize(),
                handleCrypto.deriveHandle(keypair.pub().serialize()),
                sig,
                uniqueId,
                validUntil,
                method,
                pathHash,
                bodyHash
        );
    }

    public static boolean hasAllRequestHeaders(Map<String, String> headers) {
        return (headers.containsKey("X-Goofy-Public-Key") || headers.containsKey("X-Goofy-Handle")) &&
                headers.containsKey("X-Goofy-Signature") &&
                headers.containsKey("X-Goofy-Id") &&
                headers.containsKey("X-Goofy-Valid-Until");
    }

    // This just constructs the object, it still needs to be validated!
    public static SignedRequest fromRequestHeaders(Map<String, String> headers, String method, String path, byte[] body, GenericHandleCrypto handleCrypto) throws PubSplitKeyNotFound {
        if (body == null || body.length == 0)
            body = EMPTY_BODY_VAL;

        // Get Headers
        String pubSplitKey = headers.get("X-Goofy-Public-Key");
        String handle = headers.get("X-Goofy-Handle");
        String signature = headers.get("X-Goofy-Signature");
        String uniqueIdStr = headers.get("X-Goofy-Id");
        String validUntilStr = headers.get("X-Goofy-Valid-Until");

        // Check
        if (pubSplitKey == null && handle == null)
            throw new IllegalArgumentException("Either X-Goofy-Public-Key or X-Goofy-Handle must be provided");
        if (signature == null || uniqueIdStr == null || validUntilStr == null)
            throw new IllegalArgumentException("Missing required headers for SignedRequest");
        if (method == null || path == null)
            throw new IllegalArgumentException("Method and Path must be provided");
        if (handleCrypto == null)
            throw new IllegalArgumentException("HandleCrypto must be provided");

        // Parse Strings
        long uniqueId = Long.parseLong(uniqueIdStr);
        long validUntilLong = Long.parseLong(validUntilStr);
        Instant validUntil = Instant.ofEpochMilli(validUntilLong);

        // Derive Handle / Lookup PubSplitKey
        if (handle == null)
            handle = handleCrypto.deriveHandle(pubSplitKey);
        else if (pubSplitKey == null)
            pubSplitKey = handleCrypto.getPublicSplitKeyFromHandle(handle);

        // If lookup fails, we fail
        if (handle == null || pubSplitKey == null)
            throw new PubSplitKeyNotFound(handle);

        // Strip Domain Part of Handle
        handle = GenericHandleCrypto.stripPotentialDomainFromHandle(handle);

        // Create Signed request
        return new SignedRequest(
                pubSplitKey,
                handle,
                signature,
                uniqueId,
                validUntil,
                method,
                SecretUtils.sha256(path),
                SecretUtils.sha256(body)
        );
    }

    private Map<String, String> toHeaders(boolean usePubKey, String optHandleDomain) {
        Map<String, String> headers = new HashMap<>();

        // Identity
        if (usePubKey) {
            headers.put("X-Goofy-Public-Key", this.pubSplitKey);
        } else {
            String newHandle = (optHandleDomain == null) ? this.handle : this.handle + DEF_DOMAIN_SEPARATOR + optHandleDomain;
            headers.put("X-Goofy-Handle", newHandle);
        }

        // Signature, Id, Valid Until
        headers.put("X-Goofy-Signature", this.signature);
        headers.put("X-Goofy-Id", Long.toString(this.uniqueId));
        headers.put("X-Goofy-Valid-Until", Long.toString(this.validUntil.toEpochMilli()));

        return headers;
    }


    public static SignedRequest fromRequestHeaders(Map<String, String> headers, String method, String path, GenericHandleCrypto handleCrypto) throws PubSplitKeyNotFound {return fromRequestHeaders(headers, method, path, null, handleCrypto);}
    public static SignedRequest fromParts(AsymmCrypto.AsymmFullKeyPair keypair, String method, String path, GenericHandleCrypto handleCrypto) {return fromParts(keypair, method, path, null, handleCrypto);}

    public Map<String, String> toHeadersWithPubKey() {return toHeaders(true, null);}
    public Map<String, String> toHeadersWithHandle() {return toHeaders(false, null);}
    public Map<String, String> toHeadersWithHandle(String optHandleDomain) {return toHeaders(false, optHandleDomain);}
}
