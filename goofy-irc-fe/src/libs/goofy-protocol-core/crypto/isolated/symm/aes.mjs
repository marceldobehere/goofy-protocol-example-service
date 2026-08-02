import { DEFAULT_DETERMINISTIC_SALT, symmSecretFromSecret } from "../secret-utils.mjs";
import { SymmCryptoType } from "./symm-crypto-type.mjs";

export class AES {
    getTypes() {
        return [SymmCryptoType.AES_GCM_128, SymmCryptoType.AES_GCM_192, SymmCryptoType.AES_GCM_256];
    }

    keySizeBits(type) {
        switch (type) {
            case SymmCryptoType.AES_GCM_128: return 128;
            case SymmCryptoType.AES_GCM_192: return 192;
            case SymmCryptoType.AES_GCM_256: return 256;
            default: throw new Error("Invalid type");
        }
    }

    async fromSecretString(secretStr, type) {
        const bits = this.keySizeBits(type);
        const keyBytes = await symmSecretFromSecret(
            secretStr,
            DEFAULT_DETERMINISTIC_SALT,
            bits
        );
        return keyBytes; // Uint8Array
    }

    async encrypt(dataBytes, secretBytes, type) {
        const keyBits = this.keySizeBits(type);
        if (!secretBytes || secretBytes.length * 8 !== keyBits) {
            throw new Error(`Secret length must be ${keyBits / 8} bytes for ${type.name} but is ${secretBytes}`);
        }

        const iv = new Uint8Array(12);
        crypto.getRandomValues(iv);

        const key = await crypto.subtle.importKey(
            "raw",
            secretBytes,
            { name: "AES-GCM" },
            false,
            ["encrypt"]
        );

        // Java: new GCMParameterSpec(128, iv)
        const ct = await crypto.subtle.encrypt(
            { name: "AES-GCM", iv, tagLength: 128 },
            key,
            dataBytes
        );

        const ciphertext = new Uint8Array(ct);
        const out = new Uint8Array(iv.length + ciphertext.length);
        out.set(iv, 0);
        out.set(ciphertext, iv.length);
        return out;
    }

    async decrypt(dataBytes, secretBytes, type) {
        const keyBits = this.keySizeBits(type);
        if (!secretBytes || secretBytes.length * 8 !== keyBits) {
            throw new Error(`Secret length must be ${keyBits / 8} bytes for ${type.name}`);
        }

        if (!dataBytes || dataBytes.length < 12 + 16) {
            throw new Error("Ciphertext too short");
        }

        const iv = dataBytes.slice(0, 12);
        const cipherTextAndTag = dataBytes.slice(12);

        const key = await crypto.subtle.importKey(
            "raw",
            secretBytes,
            { name: "AES-GCM" },
            false,
            ["decrypt"]
        );

        const pt = await crypto.subtle.decrypt(
            { name: "AES-GCM", iv, tagLength: 128 },
            key,
            cipherTextAndTag
        );
        return new Uint8Array(pt);
    }
}