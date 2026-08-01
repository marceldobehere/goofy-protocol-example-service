package com.masl.goofy_protocol_core.crypto.connected;

import com.masl.goofy_protocol_core.crypto.isolated.SecretUtils;
import com.masl.goofy_protocol_core.crypto.isolated.asymm.AsymmCrypto;
import com.masl.goofy_protocol_core.crypto.isolated.asymm.GlobAsymmCrypto;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class HandleCrypto implements GenericHandleCrypto {
    private final GlobAsymmCrypto asymmCrypto = new GlobAsymmCrypto();
    private final HandleCryptoHelper handleCryptoHelper;
    private final List<String> wordList;

    private Map<String, String> userKeyToHandleCache;
    private Map<String, String> generalKeyToHandleCache;
    private Map<String, String> sharedHandleToKeyCache;

    public record HandleCryptoConfig() {}

    public HandleCrypto(HandleCryptoHelper handleCryptoHelper) {
        this(handleCryptoHelper, new HandleCryptoConfig());
    }

    public HandleCrypto(HandleCryptoHelper handleCryptoHelper, HandleCryptoConfig config) {
        this.handleCryptoHelper = handleCryptoHelper;

        wordList = handleCryptoHelper.loadWordList();
        generalKeyToHandleCache = new ConcurrentHashMap<>(handleCryptoHelper.loadPersistedKeyToHandleMapCache());
        userKeyToHandleCache = new ConcurrentHashMap<>(handleCryptoHelper.loadUserKeyToHandleMap());
        generateHandleToKeyMapping();
    }

    // Persistence

    synchronized public void reloadUserKeyToHandle() {
        userKeyToHandleCache = new ConcurrentHashMap<>(handleCryptoHelper.loadUserKeyToHandleMap());
        generateHandleToKeyMapping();
    }

    synchronized public void reloadGeneralKeyToHandle() {
        generalKeyToHandleCache = new ConcurrentHashMap<>(handleCryptoHelper.loadPersistedKeyToHandleMapCache());
        generateHandleToKeyMapping();
    }

    // Generate Reverse Mappings

    synchronized public void generateHandleToKeyMapping() {
        sharedHandleToKeyCache = new ConcurrentHashMap<>();
        userKeyToHandleCache.forEach((pubSplitKey, handle) ->
                sharedHandleToKeyCache.putIfAbsent(handle, pubSplitKey));
        generalKeyToHandleCache.forEach((pubSplitKey, handle) ->
                sharedHandleToKeyCache.putIfAbsent(handle, pubSplitKey));
    }

    // Adding Entries

    public void addUserKeyToHandle(String pubSplitKey, String handle) {
        if (!verifyKeyAndHandle(pubSplitKey, handle, false))
            throw new IllegalArgumentException("Invalid key-handle pair");

        userKeyToHandleCache.putIfAbsent(pubSplitKey, handle);
        sharedHandleToKeyCache.putIfAbsent(handle, pubSplitKey);
    }

    public void addGeneralKeyToHandle(String pubSplitKey, String handle) {
        if (!verifyKeyAndHandle(pubSplitKey, handle, false))
            throw new IllegalArgumentException("Invalid key-handle pair");

        generalKeyToHandleCache.putIfAbsent(pubSplitKey, handle);
        sharedHandleToKeyCache.putIfAbsent(handle, pubSplitKey);
    }

    // Verification

    public boolean verifyKeyAndHandle(String pubSplitKey, String handle) {
        return verifyKeyAndHandle(pubSplitKey, handle, true);
    }

    public boolean verifyKeyAndHandle(AsymmCrypto.AsymmPubKeyPair pubSplitKey, String handle) {
        return verifyKeyAndHandle(pubSplitKey.serialize(), handle, true);
    }


    public boolean verifyKeyAndHandle(String pubSplitKey, String handle, boolean useCache) {
        if (useCache)
            return deriveHandle(pubSplitKey).equals(handle);
        return _internalDeriveHandle(pubSplitKey).equals(handle);
    }

    // Keypair to Handle Derivation

    public String deriveHandle(AsymmCrypto.AsymmPubKeyPair pubSplitKey) {
        return deriveHandle(pubSplitKey.serialize());
    }

    public String deriveHandle(String pubSplitKey) {
        if (userKeyToHandleCache.containsKey(pubSplitKey))
            return userKeyToHandleCache.get(pubSplitKey);

        return generalKeyToHandleCache.computeIfAbsent(pubSplitKey, _pubSplitKey -> {
            String handle = _internalDeriveHandle(_pubSplitKey);
            if (handle != null) {
                handleCryptoHelper.addPersistedKeyToHandleMapping(_pubSplitKey, handle);
                sharedHandleToKeyCache.putIfAbsent(handle, _pubSplitKey);
            }
            return handle;
        });
    }

    synchronized public String _internalDeriveHandle(String pubSplitKey) {
        // Check if it can be parsed / is valid
        AsymmCrypto.AsymmPubKeyPair pair = AsymmCrypto.AsymmPubKeyPair.parse(pubSplitKey);
        if (!pair.isSigValid(asymmCrypto.forType(pair.type())))
            throw new IllegalArgumentException("Invalid pubSplitKey");

        // Strength of handles
        // c = 2 -> ~44 bit (15000^2 * 10^5 = 2.3e13)
        // c = 3 -> ~58 bit (15000^3 * 10^5 = 3.4e17)
        // c = 4 -> ~72 bit (15000^4 * 10^5 = 5.1e21)

        byte[] rootRnd = SecretUtils.symmSecretFromSecret(pubSplitKey, SecretUtils.DEFAULT_HANDLE_ROOT_SALT, 128, SecretUtils.DEFAULT_HANDLE_ROOT_ITERATIONS);
        if (rootRnd == null)
            throw new IllegalArgumentException("Invalid pubSplitKey (unable to derive rootRnd)");
        int _c = rootRnd[0] & 0xFF;
        int _n = ((rootRnd[1] & 0xFF) << 8) | (rootRnd[2] & 0xFF);

        // Sanity Check
        if (_c < 0 || _n < 0)
            throw new IllegalArgumentException("Invalid pubSplitKey (negative c or n)");

        // Put into Bounds
        int c = 2 + _c % 3;
        int n = _n % 100000;

        // Select and write the words
        byte[] lastBytes = rootRnd;
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < c; i++) {
            String newSeed = Base64.getEncoder().encodeToString(lastBytes);
            byte[] wordRnd = SecretUtils.symmSecretFromSecret(newSeed, SecretUtils.DEFAULT_HANDLE_WORD_SALT, 128, SecretUtils.DEFAULT_HANDLE_WORD_ITERATIONS);
            if (wordRnd == null)
                throw new IllegalArgumentException("Invalid pubSplitKey (unable to derive wordRnd)");
            lastBytes = wordRnd; // use as base for next word

            int _wordIdx = ((wordRnd[0] & 0xFF) << 8) | (wordRnd[1] & 0xFF);
            int wordIndex = _wordIdx % wordList.size();

            String word = wordList.get(wordIndex);
            builder.append(word);
            if (i < c - 1)
                builder.append("_");
        }

        // Append number
        builder.append(n);
        return builder.toString();
    }

    // Handle to Keypair Lookup

    public String getPublicSplitKeyFromHandle(String handle) {
        String strippedHandle = GenericHandleCrypto.stripPotentialDomainFromHandle(handle);
        return sharedHandleToKeyCache.computeIfAbsent(strippedHandle, _ -> {
            String pubSplitKey = handleCryptoHelper.lookupPubSplitKeyForHandleExternally(handle);
            if (pubSplitKey != null) {
                handleCryptoHelper.addPersistedKeyToHandleMapping(pubSplitKey, strippedHandle);
                generalKeyToHandleCache.putIfAbsent(pubSplitKey, strippedHandle);
            }
            return pubSplitKey;
        });
    }
}
