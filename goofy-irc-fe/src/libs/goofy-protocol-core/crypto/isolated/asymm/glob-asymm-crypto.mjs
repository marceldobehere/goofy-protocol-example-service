import { AsymmCryptoType } from "./asymm-crypto-type.mjs";
import { AsymmPubKeyPair, AsymmPrivKeyPair } from "./asymm-crypto-interface.mjs";

// Implementations
import { RSA } from "./rsa.mjs";
import { EC_X } from "./ec_x.mjs";

// Base64 URL helpers
function bytesToB64Url(bytes) {
    let binary = "";
    const chunkSize = 0x8000;
    for (let i = 0; i < bytes.length; i += chunkSize) {
        binary += String.fromCharCode(...bytes.subarray(i, i + chunkSize));
    }
    return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_");
}

function b64UrlToBytes(str) {
    const s = str.replace(/-/g, "+").replace(/_/g, "/") + "===".slice((str.length + 3) % 4);
    const binary = atob(s);
    const out = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) out[i] = binary.charCodeAt(i);
    return out;
}

const textEncoder = new TextEncoder();
const textDecoder = new TextDecoder();

export class GlobAsymmCrypto {
    constructor() {
        this.cryptoList = [
            new RSA(),
            new EC_X()
        ];
    }

    getTypes() {
        return this.cryptoList.flatMap(c => c.getTypes());
    }

    forType(type) {
        return this.cryptoList.find(c => c.getTypes().includes(type)) ?? null;
    }

    async checkPublicSplitKey(pubSplitKey) {
        const parsed = AsymmPubKeyPair.parse(pubSplitKey);
        const cryptoImpl = this.forType(parsed.type);
        if (cryptoImpl === null) throw new Error("Invalid type");

        if (!cryptoImpl.checkPubKeyPair(parsed, parsed.type)) return false;
        if (!(await parsed.isSigValid(cryptoImpl))) return false;

        return true;
    }

    async generateKeypair(type = GlobAsymmCrypto.DEFAULT_TYPE) {
        const cryptoImpl = this.forType(type);
        if (cryptoImpl === null) throw new Error("Invalid type");
        return cryptoImpl.generateKeypair(type);
    }

    async encryptRaw(dataBytes, pubSplitKey) {
        const parsed = AsymmPubKeyPair.parse(pubSplitKey);
        const cryptoImpl = this.forType(parsed.type);
        if (cryptoImpl === null) throw new Error("Invalid type");

        if (!(await parsed.isSigValid(cryptoImpl))) throw new Error("Public Key Sig is not valid");

        return cryptoImpl.encrypt(dataBytes, parsed.encKey, parsed.type);
    }

    async decryptRaw(dataBytes, privSplitKey) {
        const parsed = AsymmPrivKeyPair.parse(privSplitKey);
        const cryptoImpl = this.forType(parsed.type);
        if (cryptoImpl === null) throw new Error("Invalid type");

        return cryptoImpl.decrypt(dataBytes, parsed.encKey, parsed.type);
    }

    async encrypt(dataBytes, pubSplitKey) {
        const encrypted = await this.encryptRaw(dataBytes, pubSplitKey);
        return bytesToB64Url(encrypted);
    }

    async decrypt(dataB64Url, privSplitKey) {
        const bytes = b64UrlToBytes(dataB64Url);
        return this.decryptRaw(bytes, privSplitKey);
    }

    async encryptStr(str, pubSplitKey) {
        return this.encrypt(textEncoder.encode(str), pubSplitKey);
    }

    async decryptStr(dataB64Url, privSplitKey) {
        const bytes = await this.decrypt(dataB64Url, privSplitKey);
        return textDecoder.decode(bytes);
    }

    async signRaw(dataBytes, privSplitKey) {
        const parsed = AsymmPrivKeyPair.parse(privSplitKey);
        const cryptoImpl = this.forType(parsed.type);
        if (cryptoImpl === null) throw new Error("Invalid type");

        return cryptoImpl.sign(dataBytes, parsed.sigKey, parsed.type);
    }

    async verifyRaw(dataBytes, sigBytes, pubSplitKey) {
        const parsed = AsymmPubKeyPair.parse(pubSplitKey);
        const cryptoImpl = this.forType(parsed.type);
        if (cryptoImpl === null) throw new Error("Invalid type");

        return cryptoImpl.verify(dataBytes, sigBytes, parsed.sigKey, parsed.type);
    }

    async sign(dataBytes, privSplitKey) {
        const sig = await this.signRaw(dataBytes, privSplitKey);
        return bytesToB64Url(sig);
    }

    async verify(dataBytes, sigB64Url, pubSplitKey) {
        const sigBytes = b64UrlToBytes(sigB64Url);
        return this.verifyRaw(dataBytes, sigBytes, pubSplitKey);
    }

    async signStr(str, privSplitKey) {
        return this.sign(textEncoder.encode(str), privSplitKey);
    }

    async verifyStr(str, sigB64Url, pubSplitKey) {
        return this.verify(textEncoder.encode(str), sigB64Url, pubSplitKey);
    }

    static DEFAULT_TYPE = AsymmCryptoType.EC_C25519;
}