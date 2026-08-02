import handleWords from "./handle-words.json" with { type: "json" };

export class SampleHandleHelper {
    // Load Word List (currently ~15000 entries)
    loadWordList() {
        const wordList = Array.isArray(handleWords) ? handleWords : handleWords.words;
        if (!Array.isArray(wordList)) throw new Error("Invalid handle_words.json format");
        return wordList;
    }

    // TODO: implement persistence (DB / storage)
    loadPersistedKeyToHandleMapCache() {
        return new Map(); // previously: Map.of()
    }

    // TODO: implement persistence (DB / storage)
    addPersistedKeyToHandleMapping(pubSplitKey, handle) {
        // Return boolean like the Java version; for now do nothing.
        return false;
    }

    // TODO: implement persistence (per-user storage)
    loadUserKeyToHandleMap() {
        return new Map(); // previously: Map.of()
    }

    // TODO: implement external lookup via domains / FIS
    async lookupPubSplitKeyForHandleExternally(handle) {
        const strippedHandle = this.constructor.stripPotentialDomainFromHandle(handle);
        const optDomain = this.constructor.getPotentialDomainFromHandle(handle);

        // Check internal storage / DBs for mappings (TODO)
        // Potential lookup (TODO)

        if (optDomain == null) {
            // TODO: look up by asking the domain FIS
            return null;
        } else {
            // TODO: potentially ask already known FIS servers or throw
            return null;
        }
    }

    // Minimal equivalents of GenericHandleCrypto helpers used by your Java code.
    // Adjust if your handle/domain rules differ.
    static stripPotentialDomainFromHandle(handle) {
        const at = handle.indexOf("@");
        return at >= 0 ? handle.slice(0, at) : handle;
    }

    static getPotentialDomainFromHandle(handle) {
        const at = handle.indexOf("@");
        return at >= 0 ? handle.slice(at + 1) || null : null;
    }
}