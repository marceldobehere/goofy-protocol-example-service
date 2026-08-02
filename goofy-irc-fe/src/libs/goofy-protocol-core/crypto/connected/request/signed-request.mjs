import { GlobAsymmCrypto } from "../../isolated/asymm/glob-asymm-crypto.mjs";
import { sha256 } from "../../isolated/secret-utils.mjs";
import { HandleCrypto } from "../handle-crypto.mjs"; // adjust to your real path

export const DEF_DOMAIN_SEPARATOR = "@"; // mirrors the Java constant usage

export class PubSplitKeyNotFound extends Error {
    constructor(handle) {
        super(`PubSplitKeyNotFound: ${handle}`);
        this.name = "PubSplitKeyNotFound";
        this.handle = handle;
    }
}

const asymmCrypto = new GlobAsymmCrypto();

export class SignedRequest {
    constructor(
        pubSplitKey,
        handle, // does not contain the domain part
        signature,
        uniqueId,
        validUntil, // Date
        method,
        pathHash, // Uint8Array(32)
        bodyHash // Uint8Array(32)
    ) {
        this.pubSplitKey = pubSplitKey;
        this.handle = handle;
        this.signature = signature;
        this.uniqueId = uniqueId;
        this.validUntil = validUntil;
        this.method = method;
        this.pathHash = pathHash;
        this.bodyHash = bodyHash;
    }

    static EMPTY_BODY_VAL = new Uint8Array([123]);
    static DEF_HASH_SIZE = 256 / 8; // 32
    static DEF_SEPARATOR = "#";
    static DEF_MAX_VALIDITY = 60; // seconds

    static SignedRequestValidity = Object.freeze({
        VALID: "VALID",
        MISSING_PARTS: "MISSING_PARTS",
        INVALID_HASH_SIZE: "INVALID_HASH_SIZE",
        INVALID_TIME: "INVALID_TIME",
        INVALID_ID: "INVALID_ID",
        HANDLE_MISMATCH: "HANDLE_MISMATCH",
        INVALID_SIGNATURE: "INVALID_SIGNATURE",
    });

    isValid(handleCrypto, validator) {
        if (handleCrypto == null || validator == null) return SignedRequest.SignedRequestValidity.MISSING_PARTS;

        // Check general validity
        if (
            this.pubSplitKey == null ||
            this.handle == null ||
            this.signature == null ||
            this.validUntil == null ||
            this.method == null ||
            this.pathHash == null ||
            this.bodyHash == null
        ) {
            return SignedRequest.SignedRequestValidity.MISSING_PARTS;
        }
        if (
            this.pathHash.length !== SignedRequest.DEF_HASH_SIZE ||
            this.bodyHash.length !== SignedRequest.DEF_HASH_SIZE
        ) {
            return SignedRequest.SignedRequestValidity.INVALID_HASH_SIZE;
        }

        // Check time based and unique validity
        if (!validator.isValidUntilValid(this.validUntil)) return SignedRequest.SignedRequestValidity.INVALID_TIME;
        if (!validator.isUniqueIdValid(this.uniqueId)) return SignedRequest.SignedRequestValidity.INVALID_ID;

        // Check Public Key
        // (async in JS; but Java version is sync. We support sync return values here by treating verifyKeyAndHandle as async below.)
        // NOTE: handleCrypto methods are async in your HandleCrypto; to keep API consistent with your crypto impl (async),
        // we make isValid async-compatible.
    }

    async isValidAsync(handleCrypto, validator) {
        if (handleCrypto == null || validator == null) return SignedRequest.SignedRequestValidity.MISSING_PARTS;

        // Check general validity
        if (
            this.pubSplitKey == null ||
            this.handle == null ||
            this.signature == null ||
            this.validUntil == null ||
            this.method == null ||
            this.pathHash == null ||
            this.bodyHash == null
        ) {
            return SignedRequest.SignedRequestValidity.MISSING_PARTS;
        }
        if (
            this.pathHash.length !== SignedRequest.DEF_HASH_SIZE ||
            this.bodyHash.length !== SignedRequest.DEF_HASH_SIZE
        ) {
            return SignedRequest.SignedRequestValidity.INVALID_HASH_SIZE;
        }

        // Check time based and unique validity
        if (!validator.isValidUntilValid(this.validUntil)) return SignedRequest.SignedRequestValidity.INVALID_TIME;
        if (!validator.isUniqueIdValid(this.uniqueId)) return SignedRequest.SignedRequestValidity.INVALID_ID;

        // Check Public Key
        try {
            await asymmCrypto.checkPublicSplitKey(this.pubSplitKey);
        } catch {
            // if check fails we treat as handle mismatch / invalid signature-ish; Java returns handle mismatch later,
            // but there is no dedicated "INVALID_PUBKEY". We'll map to HANDLE_MISMATCH.
            return SignedRequest.SignedRequestValidity.HANDLE_MISMATCH;
        }

        // Check Handle against Public Key
        const okHandle = await handleCrypto.verifyKeyAndHandle(this.pubSplitKey, this.handle);
        if (!okHandle) return SignedRequest.SignedRequestValidity.HANDLE_MISMATCH;

        // Check Signature
        const baseObj =
            this.method +
            SignedRequest.DEF_SEPARATOR +
            this.uniqueId +
            SignedRequest.DEF_SEPARATOR +
            this.validUntil.getTime() +
            SignedRequest.DEF_SEPARATOR +
            SignedRequest._b64UrlEncode(this.pathHash) +
            SignedRequest.DEF_SEPARATOR +
            SignedRequest._b64UrlEncode(this.bodyHash);

        try {
            const okSig = await asymmCrypto.verifyStr(baseObj, this.signature, this.pubSplitKey);
            if (!okSig) return SignedRequest.SignedRequestValidity.INVALID_SIGNATURE;
        } catch (e) {
            return SignedRequest.SignedRequestValidity.INVALID_SIGNATURE;
        }

        return SignedRequest.SignedRequestValidity.VALID;
    }

    static _b64UrlEncode(bytes) {
        let binary = "";
        const chunkSize = 0x8000;
        for (let i = 0; i < bytes.length; i += chunkSize) {
            binary += String.fromCharCode(...bytes.subarray(i, i + chunkSize));
        }
        return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_");
    }

    static _randomUniqueId() {
        return crypto.getRandomValues(new BigInt64Array(1))[0];
    }

    static async fromParts(keypair, method, path, body, handleCrypto) {
        if (body == null || body.length === 0) body = SignedRequest.EMPTY_BODY_VAL;

        if (method == null || path == null) throw new Error("Method and Path must be provided");
        if (handleCrypto == null) throw new Error("HandleCrypto must be provided");

        const pathHash = await sha256(path);
        const bodyHash = await sha256(body);

        const uniqueId = SignedRequest._randomUniqueId();
        const validUntil = new Date(Date.now() + SignedRequest.DEF_MAX_VALIDITY * 1000);

        const baseObj =
            method +
            SignedRequest.DEF_SEPARATOR +
            uniqueId +
            SignedRequest.DEF_SEPARATOR +
            validUntil.getTime() +
            SignedRequest.DEF_SEPARATOR +
            SignedRequest._b64UrlEncode(pathHash) +
            SignedRequest.DEF_SEPARATOR +
            SignedRequest._b64UrlEncode(bodyHash);

        const sig = await asymmCrypto.signStr(baseObj, keypair.priv.serialize());

        const pubSplitKey = keypair.pub.serialize();
        const handle = await handleCrypto.deriveHandle(pubSplitKey);

        return new SignedRequest(pubSplitKey, handle, sig, uniqueId, validUntil, method, pathHash, bodyHash);
    }

    static hasAllRequestHeaders(headers) {
        return (
            (headers.has("X-Goofy-Public-Key") || headers.has("X-Goofy-Handle")) &&
            headers.has("X-Goofy-Signature") &&
            headers.has("X-Goofy-Id") &&
            headers.has("X-Goofy-Valid-Until")
        );
    }

    static async fromRequestHeaders(headers, method, path, body, handleCrypto) {
        if (body == null || body.length === 0) body = SignedRequest.EMPTY_BODY_VAL;

        const pubSplitKeyIn = headers.get("X-Goofy-Public-Key") ?? null;
        const handleIn = headers.get("X-Goofy-Handle") ?? null;
        const signature = headers.get("X-Goofy-Signature") ?? null;
        const uniqueIdStr = headers.get("X-Goofy-Id") ?? null;
        const validUntilStr = headers.get("X-Goofy-Valid-Until") ?? null;

        if (pubSplitKeyIn == null && handleIn == null) {
            throw new Error("Either X-Goofy-Public-Key or X-Goofy-Handle must be provided");
        }
        if (signature == null || uniqueIdStr == null || validUntilStr == null) {
            throw new Error("Missing required headers for SignedRequest");
        }
        if (method == null || path == null) throw new Error("Method and Path must be provided");
        if (handleCrypto == null) throw new Error("HandleCrypto must be provided");

        const uniqueId = BigInt(uniqueIdStr);
        const validUntilLong = Number.parseInt(validUntilStr, 10);
        const validUntil = new Date(validUntilLong);

        let pubSplitKey = pubSplitKeyIn;
        let handle = handleIn;

        // Derive Handle / Lookup PubSplitKey
        if (handle == null) {
            handle = await handleCrypto.deriveHandle(pubSplitKey);
        } else if (pubSplitKey == null) {
            pubSplitKey = await handleCrypto.getPublicSplitKeyFromHandle(handle);
        }

        if (handle == null || pubSplitKey == null) throw new PubSplitKeyNotFound(handle);

        // Strip Domain Part of Handle
        handle = HandleCrypto.stripPotentialDomainFromHandle(handle);

        const pathHash = await sha256(path);
        const bodyHash = await sha256(body);

        return new SignedRequest(pubSplitKey, handle, signature, uniqueId, validUntil, method, pathHash, bodyHash);
    }

    static async fromRequestHeadersSimple(headers, method, path, handleCrypto) {
        return SignedRequest.fromRequestHeaders(headers, method, path, null, handleCrypto);
    }

    static async fromPartsSimple(keypair, method, path, handleCrypto) {
        return SignedRequest.fromParts(keypair, method, path, null, handleCrypto);
    }

    _toHeaders(usePubKey, optHandleDomain) {
        const headers = new Map();

        if (usePubKey) {
            headers.set("X-Goofy-Public-Key", this.pubSplitKey);
        } else {
            const newHandle = optHandleDomain == null ? this.handle : this.handle + DEF_DOMAIN_SEPARATOR + optHandleDomain;
            headers.set("X-Goofy-Handle", newHandle);
        }

        headers.set("X-Goofy-Signature", this.signature);
        headers.set("X-Goofy-Id", String(this.uniqueId));
        headers.set("X-Goofy-Valid-Until", String(this.validUntil.getTime()));

        return headers;
    }

    toHeadersWithPubKey() {
        return this._toHeaders(true, null);
    }

    toHeadersWithHandle(optHandleDomain = null) {
        return this._toHeaders(false, optHandleDomain);
    }
}