import {AsymmCrypto, AsymmFullKeyPair} from "./asymm-crypto-interface.mjs";
import { AsymmCryptoType } from "./asymm-crypto-type.mjs";
import { GlobSymmCrypto } from "../symm/glob-symm-crypto.mjs";

const cryptoObj = new GlobSymmCrypto();

function getUint16BE(a, offset) {
    return ((a[offset] & 0xff) << 8) | (a[offset + 1] & 0xff);
}

function setUint16BE(out, offset, value) {
    out[offset] = (value >>> 8) & 0xff;
    out[offset + 1] = value & 0xff;
}

// WebCrypto key import/export
async function importRsaPublicKeyFromX509(spkiBytes) {
    return crypto.subtle.importKey(
        "spki",
        spkiBytes,
        { name: "RSA-OAEP", hash: "SHA-256" },
        false,
        ["encrypt"]
    );
}

async function importRsaPrivateKeyFromPkcs8(pkcs8Bytes) {
    return crypto.subtle.importKey(
        "pkcs8",
        pkcs8Bytes,
        { name: "RSA-OAEP", hash: "SHA-256" },
        false,
        ["decrypt"]
    );
}

function keySizeBits(type) {
    switch (type) {
        case AsymmCryptoType.RSA_2048: return 2048;
        case AsymmCryptoType.RSA_3072: return 3072;
        case AsymmCryptoType.RSA_4096: return 4096;
        default: throw new Error("Invalid type");
    }
}

// WebCrypto RSA-OAEP max plaintext depends on hash, usually:
// k - 2*hLen - 2 (k = modulus length in bytes, hLen = 32 for SHA-256)
function maxContentSize(type) {
    const k = keySizeBits(type) / 8;
    const hLen = 32; // SHA-256
    return k - 2 * hLen - 10;
}

export class RSA extends AsymmCrypto {
    getTypes() {
        return [AsymmCryptoType.RSA_2048, AsymmCryptoType.RSA_3072, AsymmCryptoType.RSA_4096];
    }

    async checkPubKeyPair(pubKeyPair, type) {
        try {
            if (!pubKeyPair || !(pubKeyPair.encKey instanceof Uint8Array) || !(pubKeyPair.sigKey instanceof Uint8Array)) {
                return false;
            }

            // Java: for (encKey, sigKey) try parse as X509 public + init RSA cipher for ENCRYPT_MODE
            // WebCrypto equivalent: import SPKI as RSA public key and ensure it's usable for encrypt.
            const encPubKey = await importRsaPublicKeyFromX509(pubKeyPair.encKey);
            await crypto.subtle.encrypt(
                { name: "RSA-OAEP" },
                encPubKey,
                new Uint8Array([0x00])
            );

            const sigPubKey = await importRsaPublicKeyFromX509(pubKeyPair.sigKey);
            await crypto.subtle.encrypt(
                { name: "RSA-OAEP" },
                sigPubKey,
                new Uint8Array([0x00])
            );

            return true;
        } catch {
            return false;
        }
    }

    async generateKeypair(type) {
        const bits = keySizeBits(type);

        const keyPair = await crypto.subtle.generateKey(
            {
                name: "RSA-OAEP",
                modulusLength: bits,
                publicExponent: new Uint8Array([0x01, 0x00, 0x01]),
                hash: "SHA-256",
            },
            true,
            ["encrypt", "decrypt"]
        );

        // Export in the same shapes the Java code expects:
        // X509 SPKI for public, PKCS8 for private.
        const pubSpki = new Uint8Array(await crypto.subtle.exportKey("spki", keyPair.publicKey));
        const privPkcs8 = new Uint8Array(await crypto.subtle.exportKey("pkcs8", keyPair.privateKey));

        return await AsymmFullKeyPair.fromParts(
            pubSpki, // pubSigKey
            pubSpki, // pubEncKey
            privPkcs8, // privSigKey
            privPkcs8, // privEncKey
            this,
            type
        );
    }

    async _encryptSmall(dataBytes, pubEncKeySpki) {
        const publicKey = await importRsaPublicKeyFromX509(pubEncKeySpki);
        // RSA-OAEP(SHA-256)
        const ciphertext = await crypto.subtle.encrypt({ name: "RSA-OAEP" }, publicKey, dataBytes);
        return new Uint8Array(ciphertext);
    }

    async _decryptSmall(ciphertextBytes, privEncKeyPkcs8) {
        const privateKey = await importRsaPrivateKeyFromPkcs8(privEncKeyPkcs8);
        const plaintext = await crypto.subtle.decrypt({ name: "RSA-OAEP" }, privateKey, ciphertextBytes);
        return new Uint8Array(plaintext);
    }

    async _encryptBig(dataBytes, pubEncKeySpki) {
        // Mirror Java logic:
        // 1) generate 32-byte symmetric key
        // 2) encrypt data using GlobSymmCrypto.encryptRaw(data, ISO-8859-1 string of symmKey bytes)
        // 3) encrypt symmetric key with RSA
        //
        // NOTE: WebCrypto can't do "ISO_8859_1 string directly from bytes" without text conversion.
        // Java did: new String(symmKey, ISO_8859_1)
        // We'll replicate that by mapping each byte 0..255 to same code point char.
        const SYMM_KEY_SIZE = 32;

        const symmKey = new Uint8Array(SYMM_KEY_SIZE);
        crypto.getRandomValues(symmKey);

        const symmKeyStr = Array.from(symmKey, b => String.fromCharCode(b)).join("");

        const dataEnc = await cryptoObj.encryptRaw(dataBytes, symmKeyStr);

        const symmKeyEnc = await this._encryptSmall(symmKey, pubEncKeySpki);

        if (symmKeyEnc.length > 0xffff) {
            throw new Error("Encrypted symmetric key too large for short length: " + symmKeyEnc.length);
        }

        const out = new Uint8Array(2 + symmKeyEnc.length + dataEnc.length);
        setUint16BE(out, 0, symmKeyEnc.length);
        out.set(symmKeyEnc, 2);
        out.set(dataEnc, 2 + symmKeyEnc.length);
        return out;
    }

    async _decryptBig(payloadBytes, privEncKeyPkcs8) {
        if (payloadBytes.length < 2) throw new Error("Ciphertext too short.");

        const symmKeyEncLen = getUint16BE(payloadBytes, 0);
        if (payloadBytes.length < 2 + symmKeyEncLen) {
            throw new Error("Ciphertext malformed (bad symmKey length).");
        }

        const symmKeyEnc = payloadBytes.slice(2, 2 + symmKeyEncLen);
        const dataEnc = payloadBytes.slice(2 + symmKeyEncLen);

        const symmKey = await this._decryptSmall(symmKeyEnc, privEncKeyPkcs8);

        const symmKeyStr = Array.from(symmKey, b => String.fromCharCode(b)).join("");

        return cryptoObj.decryptRaw(dataEnc, symmKeyStr);
    }

    async encrypt(dataBytes, pubEncKeySpki, type) {
        const small = dataBytes.length <= maxContentSize(type);
        const res = small ? await this._encryptSmall(dataBytes, pubEncKeySpki) : await this._encryptBig(dataBytes, pubEncKeySpki);

        const out = new Uint8Array(1 + res.length);
        out[0] = small ? 1 : 0;
        out.set(res, 1);
        return out;
    }

    async decrypt(dataBytes, privEncKeyPkcs8, type) {
        if (dataBytes.length < 2) throw new Error("Ciphertext too short.");

        const small = dataBytes[0] === 1;
        const payload = dataBytes.slice(1);
        return small ? await this._decryptSmall(payload, privEncKeyPkcs8) : await this._decryptBig(payload, privEncKeyPkcs8);
    }

    async sign(dataBytes, privSigKeyPkcs8, type) {
        const privateKey = await crypto.subtle.importKey(
            "pkcs8",
            privSigKeyPkcs8,
            { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
            false,
            ["sign"]
        );

        const sig = await crypto.subtle.sign({ name: "RSASSA-PKCS1-v1_5" }, privateKey, dataBytes);
        return new Uint8Array(sig);
    }

    async verify(dataBytes, sigBytes, pubSigKeySpki, type) {
        // Java verify: SHA256withRSA
        const publicKey = await crypto.subtle.importKey(
            "spki",
            pubSigKeySpki,
            { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
            false,
            ["verify"]
        );

        return await crypto.subtle.verify({ name: "RSASSA-PKCS1-v1_5" }, publicKey, sigBytes, dataBytes);
    }
}