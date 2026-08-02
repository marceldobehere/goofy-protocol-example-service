import { ENC_DELIMITER } from "../secret-utils.mjs";
import { AsymmCryptoType } from "./asymm-crypto-type.mjs";

function b64uEncode(bytes) {
    // bytes: Uint8Array
    let binary = "";
    const chunkSize = 0x8000;
    for (let i = 0; i < bytes.length; i += chunkSize) {
        binary += String.fromCharCode(...bytes.subarray(i, i + chunkSize));
    }
    return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_");
}

function b64uDecode(str) {
    const s = str.replace(/-/g, "+").replace(/_/g, "/") + "===".slice((str.length + 3) % 4);
    const binary = atob(s);
    const out = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) out[i] = binary.charCodeAt(i);
    return out;
}

function splitKey(value) {
    return value.split(ENC_DELIMITER);
}

export class AsymmPubKeyPair {
    /**
     * @param {Uint8Array} sigKey
     * @param {Uint8Array} encKey
     * @param {Uint8Array|null} encSig
     * @param {object} type (from AsymmCryptoType)
     */
    constructor(sigKey, encKey, encSig, type) {
        this.sigKey = sigKey;
        this.encKey = encKey;
        this.encSig = encSig;
        this.type = type;
    }

    static parse(value) {
        const parts = splitKey(value);
        if (parts.length !== 5) throw new Error("Invalid split key format");
        if (parts[0] !== "PUB") throw new Error("Invalid split key type");

        const type = AsymmCryptoType[parts[1]];
        if (!type) throw new Error(`Unknown AsymmCryptoType name: ${parts[1]}`);

        const sigKey = b64uDecode(parts[2]);

        // Java logic:
        // if (parts[3].equals(parts[4]) && parts[3].equals("X")) { return sigKey, sigKey, null }
        if (parts[3] === parts[4] && parts[3] === "X") {
            return new AsymmPubKeyPair(sigKey, sigKey, null, type);
        }

        const encKey = b64uDecode(parts[3]);
        const encSig = b64uDecode(parts[4]);
        return new AsymmPubKeyPair(sigKey, encKey, encSig, type);
    }

    serialize() {
        const sigKeyEqualsEncKey =
            this.sigKey.length === this.encKey.length &&
            this.sigKey.every((v, i) => v === this.encKey[i]);

        if (sigKeyEqualsEncKey) {
            return (
                "PUB" +
                ENC_DELIMITER +
                this.type.name +
                ENC_DELIMITER +
                b64uEncode(this.sigKey) +
                ENC_DELIMITER +
                "X" +
                ENC_DELIMITER +
                "X"
            );
        }

        return (
            "PUB" +
            ENC_DELIMITER +
            this.type.name +
            ENC_DELIMITER +
            b64uEncode(this.sigKey) +
            ENC_DELIMITER +
            b64uEncode(this.encKey) +
            ENC_DELIMITER +
            b64uEncode(this.encSig)
        );
    }

    async isSigValid(cryptoImpl) {
        const sigKeyEqualsEncKey =
            this.sigKey.length === this.encKey.length &&
            this.sigKey.every((v, i) => v === this.encKey[i]);

        if (sigKeyEqualsEncKey) return true;
        // verify(encKey, encSig, sigKey, type)
        return cryptoImpl.verify(this.encKey, this.encSig, this.sigKey, this.type);
    }
}

export class AsymmPrivKeyPair {
    /**
     * @param {Uint8Array} sigKey
     * @param {Uint8Array} encKey
     * @param {object} type
     */
    constructor(sigKey, encKey, type) {
        this.sigKey = sigKey;
        this.encKey = encKey;
        this.type = type;
    }

    static parse(value) {
        const parts = splitKey(value);
        if (parts.length !== 4) throw new Error("Invalid split key format");
        if (parts[0] !== "PRIV") throw new Error("Invalid split key type");

        const type = AsymmCryptoType[parts[1]];
        if (!type) throw new Error(`Unknown AsymmCryptoType name: ${parts[1]}`);

        const sigKey = b64uDecode(parts[2]);

        // If parts[3].equals("X") => sigKey == encKey
        if (parts[3] === "X") {
            return new AsymmPrivKeyPair(sigKey, sigKey, type);
        }

        const encKey = b64uDecode(parts[3]);
        return new AsymmPrivKeyPair(sigKey, encKey, type);
    }

    serialize() {
        const sigKeyEqualsEncKey =
            this.sigKey.length === this.encKey.length &&
            this.sigKey.every((v, i) => v === this.encKey[i]);

        if (sigKeyEqualsEncKey) {
            return (
                "PRIV" +
                ENC_DELIMITER +
                this.type.name +
                ENC_DELIMITER +
                b64uEncode(this.sigKey) +
                ENC_DELIMITER +
                "X"
            );
        }

        return (
            "PRIV" +
            ENC_DELIMITER +
            this.type.name +
            ENC_DELIMITER +
            b64uEncode(this.sigKey) +
            ENC_DELIMITER +
            b64uEncode(this.encKey)
        );
    }
}

export class AsymmFullKeyPair {
    constructor(pub, priv) {
        this.pub = pub;
        this.priv = priv;
    }

    static async fromParts(pubSigKey, pubEncKey, privSigKey, privEncKey, cryptoImpl, type) {
        const priv = new AsymmPrivKeyPair(privSigKey, privEncKey, type);

        // Java:
        // byte[] encSig = Arrays.equals(pubEncKey, pubSigKey) ? null : crypto.sign(pubEncKey, privSigKey, type);
        const encSig =
            pubEncKey.length === pubSigKey.length && pubEncKey.every((v, i) => v === pubSigKey[i])
                ? null
                : await cryptoImpl.sign(pubEncKey, privSigKey, type);

        const pub = new AsymmPubKeyPair(pubSigKey, pubEncKey, encSig, type);
        return new AsymmFullKeyPair(pub, priv);
    }

    static fromSplitKeys(pubSplitKey, privSplitKey) {
        return new AsymmFullKeyPair(
            AsymmPubKeyPair.parse(pubSplitKey),
            AsymmPrivKeyPair.parse(privSplitKey)
        );
    }
}

// "Interface" (documentation + runtime expectations)
export class AsymmCrypto {
    getTypes() {
        throw new Error("Not implemented");
    }
    async checkPubKeyPair(pubKeyPair, type) {
        throw new Error("Not implemented");
    }
    async generateKeypair(type) {
        throw new Error("Not implemented");
    }

    async encrypt(data, pubEncKey, type) {
        throw new Error("Not implemented");
    }
    async decrypt(data, privEncKey, type) {
        throw new Error("Not implemented");
    }

    async sign(data, privSigKey, type) {
        throw new Error("Not implemented");
    }
    async verify(data, sig, pubSigKey, type) {
        throw new Error("Not implemented");
    }
}

export { b64uEncode, b64uDecode };