import { AsymmPubKeyPair } from "../isolated/asymm/asymm-crypto-interface.mjs";
import { GlobAsymmCrypto } from "../isolated/asymm/glob-asymm-crypto.mjs";
import {
    symmSecretFromSecret,
    DEFAULT_HANDLE_ROOT_ITERATIONS,
    DEFAULT_HANDLE_WORD_ITERATIONS,
    DEFAULT_HANDLE_ROOT_SALT,
    DEFAULT_HANDLE_WORD_SALT,
} from "../isolated/secret-utils.mjs";


// ---- HandleCrypto ----

export class HandleCrypto {
    /**
     * @param {object} handleCryptoHelper
     * @param {object} [config]
     */
    constructor(handleCryptoHelper, config = {}) {
        this.asymmCrypto = new GlobAsymmCrypto();
        this.handleCryptoHelper = handleCryptoHelper;

        this.wordList = handleCryptoHelper.loadWordList();

        // Maps: key->handle and handle->key
        this.generalKeyToHandleCache = new Map(handleCryptoHelper.loadPersistedKeyToHandleMapCache());
        this.userKeyToHandleCache = new Map(handleCryptoHelper.loadUserKeyToHandleMap());

        this.sharedHandleToKeyCache = new Map();
        this.generateHandleToKeyMapping();
    }

    // Persistence
    reloadUserKeyToHandle() {
        this.userKeyToHandleCache = new Map(this.handleCryptoHelper.loadUserKeyToHandleMap());
        this.generateHandleToKeyMapping();
    }

    reloadGeneralKeyToHandle() {
        this.generalKeyToHandleCache = new Map(this.handleCryptoHelper.loadPersistedKeyToHandleMapCache());
        this.generateHandleToKeyMapping();
    }

    // Generate reverse mappings
    generateHandleToKeyMapping() {
        this.sharedHandleToKeyCache = new Map();

        for (const [pubSplitKey, handle] of this.userKeyToHandleCache.entries()) {
            if (!this.sharedHandleToKeyCache.has(handle)) this.sharedHandleToKeyCache.set(handle, pubSplitKey);
        }
        for (const [pubSplitKey, handle] of this.generalKeyToHandleCache.entries()) {
            if (!this.sharedHandleToKeyCache.has(handle)) this.sharedHandleToKeyCache.set(handle, pubSplitKey);
        }
    }

    // Adding Entries
    async addUserKeyToHandle(pubSplitKey, handle) {
        const ok = await this.verifyKeyAndHandle(pubSplitKey, handle, false);
        if (!ok) throw new Error("Invalid key-handle pair");

        this.userKeyToHandleCache.set(pubSplitKey, this.userKeyToHandleCache.has(pubSplitKey) ? this.userKeyToHandleCache.get(pubSplitKey) : handle);
        if (!this.sharedHandleToKeyCache.has(handle)) this.sharedHandleToKeyCache.set(handle, pubSplitKey);
    }

    async addGeneralKeyToHandle(pubSplitKey, handle) {
        const ok = await this.verifyKeyAndHandle(pubSplitKey, handle, false);
        if (!ok) throw new Error("Invalid key-handle pair");

        this.generalKeyToHandleCache.set(pubSplitKey, this.generalKeyToHandleCache.has(pubSplitKey) ? this.generalKeyToHandleCache.get(pubSplitKey) : handle);
        if (!this.sharedHandleToKeyCache.has(handle)) this.sharedHandleToKeyCache.set(handle, pubSplitKey);
    }

    // Verification

    async verifyKeyAndHandleFromPair(pubSplitKeyPair, handle) {
        return this.verifyKeyAndHandle(pubSplitKeyPair.serialize(), handle, true);
    }

    async verifyKeyAndHandle(pubSplitKey, handle, useCache = true) {
        if (useCache) {
            const derived = await this.deriveHandle(pubSplitKey);
            return derived === handle;
        }
        const derived = await this._internalDeriveHandle(pubSplitKey);
        return derived === handle;
    }

    // Keypair to Handle Derivation
    async deriveHandle(pubSplitKeyOrPair) {
        const pubSplitKey =
            pubSplitKeyOrPair instanceof AsymmPubKeyPair ? pubSplitKeyOrPair.serialize() : pubSplitKeyOrPair;

        if (this.userKeyToHandleCache.has(pubSplitKey)) {
            return this.userKeyToHandleCache.get(pubSplitKey);
        }

        // JS equivalent of computeIfAbsent (async) with simple memoization
        if (this.generalKeyToHandleCache.has(pubSplitKey)) {
            return this.generalKeyToHandleCache.get(pubSplitKey);
        }

        const handle = await this._internalDeriveHandle(pubSplitKey);
        if (handle != null) {
            this.handleCryptoHelper.addPersistedKeyToHandleMapping(pubSplitKey, handle);
            if (!this.sharedHandleToKeyCache.has(handle)) this.sharedHandleToKeyCache.set(handle, pubSplitKey);
            this.generalKeyToHandleCache.set(pubSplitKey, handle);
        }
        return handle;
    }

    async _internalDeriveHandle(pubSplitKey) {
        const pair = AsymmPubKeyPair.parse(pubSplitKey);

        const cryptoImpl = this.asymmCrypto.forType(pair.type);
        if (!cryptoImpl) throw new Error("Invalid pubSplitKey");
        const sigOk = await pair.isSigValid(cryptoImpl);
        if (!sigOk) throw new Error("Invalid pubSplitKey");

        // Strength of handles
        const rootRnd = await symmSecretFromSecret(
            pubSplitKey,
            DEFAULT_HANDLE_ROOT_SALT,
            128,
            DEFAULT_HANDLE_ROOT_ITERATIONS
        );
        if (rootRnd == null) throw new Error("Invalid pubSplitKey (unable to derive rootRnd)");

        const _c = rootRnd[0] & 0xff;
        const _n = ((rootRnd[1] & 0xff) << 8) | (rootRnd[2] & 0xff);

        if (_c < 0 || _n < 0) throw new Error("Invalid pubSplitKey (negative c or n)");

        const c = 2 + (_c % 3);
        const n = _n % 100000;

        // Append words derived from successive PBKDF2 steps
        let lastBytes = rootRnd;
        const parts = [];
        for (let i = 0; i < c; i++) {
            const newSeed = base64Encode(lastBytes);
            const wordRnd = await symmSecretFromSecret(
                newSeed,
                DEFAULT_HANDLE_WORD_SALT,
                128,
                DEFAULT_HANDLE_WORD_ITERATIONS
            );
            if (wordRnd == null) throw new Error("Invalid pubSplitKey (unable to derive wordRnd)");

            lastBytes = wordRnd;

            const _wordIdx = ((wordRnd[0] & 0xff) << 8) | (wordRnd[1] & 0xff);
            const wordIndex = _wordIdx % this.wordList.length;

            parts.push(this.wordList[wordIndex]);
        }

        return `${parts.join("_")}${n}`;
    }

    // Handle to Keypair Lookup
    async getPublicSplitKeyFromHandle(handle) {
        const strippedHandle = this.constructor.stripPotentialDomainFromHandle(handle);

        if (this.sharedHandleToKeyCache.has(strippedHandle)) {
            return this.sharedHandleToKeyCache.get(strippedHandle);
        }

        const pubSplitKey = await this.handleCryptoHelper.lookupPubSplitKeyForHandleExternally(handle);

        if (pubSplitKey != null) {
            this.handleCryptoHelper.addPersistedKeyToHandleMapping(pubSplitKey, strippedHandle);
            this.generalKeyToHandleCache.set(pubSplitKey, strippedHandle);
            if (!this.sharedHandleToKeyCache.has(strippedHandle)) this.sharedHandleToKeyCache.set(strippedHandle, pubSplitKey);
        }

        return pubSplitKey;
    }

    // ---- Domain stripping (you had GenericHandleCrypto.stripPotentialDomainFromHandle) ----
    static stripPotentialDomainFromHandle(handle) {
        // If your JS environment has GenericHandleCrypto, you can replace this with the real call.
        // Common patterns: "handle@domain", "domain:handle", etc.
        // This is a safe default: return the substring before '@' if present.
        const at = handle.indexOf("@");
        return at >= 0 ? handle.slice(0, at) : handle;
    }
}

// ---- Base64 for seeds (Java used Base64.getEncoder().encodeToString(byte[])) ----
function base64Encode(bytes) {
    let binary = "";
    const chunkSize = 0x8000;
    for (let i = 0; i < bytes.length; i += chunkSize) {
        binary += String.fromCharCode(...bytes.subarray(i, i + chunkSize));
    }
    return btoa(binary);
}