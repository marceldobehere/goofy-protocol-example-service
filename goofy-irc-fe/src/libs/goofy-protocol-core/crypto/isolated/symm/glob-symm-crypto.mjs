import { ENC_DELIMITER } from "../secret-utils.mjs";
import { SymmCryptoType } from "./symm-crypto-type.mjs";

// Implementations
import { AES } from "./aes.mjs";
import { ChaCha20 } from "./chacha20.mjs";

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

function u16beToInt(x) {
    return ((x[0] & 0xff) << 8) | (x[1] & 0xff);
}
function intToU16be(n) {
    return new Uint8Array([(n >>> 8) & 0xff, n & 0xff]);
}

export class GlobSymmCrypto {
    constructor() {
        this.cryptoList = [
            new AES(),
            new ChaCha20()
        ];
    }

    getTypes() {
        return this.cryptoList.flatMap(c => c.getTypes());
    }

    forType(type) {
        return this.cryptoList.find(c => c.getTypes().includes(type)) ?? null;
    }

    static ParsedEncData = class ParsedEncData {
        constructor(data, type) {
            this.data = data; // Uint8Array
            this.type = type; // SymmCryptoType
        }

        static parse(value) {
            const parts = value.split(ENC_DELIMITER);
            if (parts.length !== 2) throw new Error("Invalid data format");
            const type = SymmCryptoType[parts[0]];
            if (!type) throw new Error(`Unknown SymmCryptoType name: ${parts[0]}`);
            const data = b64UrlToBytes(parts[1]);
            return new GlobSymmCrypto.ParsedEncData(data, type);
        }

        serialize() {
            return `${this.type.name}${ENC_DELIMITER}${bytesToB64Url(this.data)}`;
        }
    };

    async encryptStr(data, secret, type = GlobSymmCrypto.DEFAULT_TYPE) {
        return this.encrypt(new TextEncoder().encode(data), secret, type);
    }

    async decryptStr(data, secret) {
        const bytes = await this.decrypt(data, secret);
        return bytes !== null ? new TextDecoder().decode(bytes) : null;
    }

    async encrypt(dataBytes, secret, type = GlobSymmCrypto.DEFAULT_TYPE) {
        const crypto = this.forType(type);
        if (!crypto || dataBytes == null || secret == null) throw new Error("Invalid type, data or secret");
        const res = await crypto.encrypt(dataBytes, await crypto.fromSecretString(secret, type), type);
        return new GlobSymmCrypto.ParsedEncData(res, type).serialize();
    }

    async decrypt(data, secret) {
        const parsed = GlobSymmCrypto.ParsedEncData.parse(data);
        const crypto = this.forType(parsed.type);
        if (!crypto || secret == null) throw new Error("Invalid type or secret");
        return crypto.decrypt(parsed.data, await crypto.fromSecretString(secret, parsed.type), parsed.type);
    }

    async encryptRaw(dataBytes, secret, type = GlobSymmCrypto.DEFAULT_TYPE) {
        const crypto = this.forType(type);
        if (!crypto || dataBytes == null || secret == null) throw new Error("Invalid type, data or secret");

        const res = await crypto.encrypt(dataBytes, await crypto.fromSecretString(secret, type), type);

        // Java: ByteBuffer.allocate(2 + res.length); buf.putShort(type.getValue()); buf.put(res)
        // -> big-endian u16
        const out = new Uint8Array(2 + res.length);
        const u16 = intToU16be(type.value);
        out.set(u16, 0);
        out.set(res, 2);
        return out;
    }

    async decryptRaw(dataBytes, secret) {
        if (!dataBytes || secret == null || dataBytes.length < 2) throw new Error("Invalid type or secret");

        const typeValue = u16beToInt(dataBytes.subarray(0, 2));
        const type = SymmCryptoType.fromValue(typeValue); // you provide this method in the enum module

        const crypto = this.forType(type);
        if (!crypto) throw new Error("Invalid type or secret");

        const enc = dataBytes.subarray(2);
        return crypto.decrypt(enc, await crypto.fromSecretString(secret, type), type);
    }

    static DEFAULT_TYPE = SymmCryptoType.AES_GCM_256;
}