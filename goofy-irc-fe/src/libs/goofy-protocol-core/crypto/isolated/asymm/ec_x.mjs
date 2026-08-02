import { AsymmCrypto } from "./asymm-crypto-interface.mjs";
import { AsymmFullKeyPair } from "./asymm-crypto-interface.mjs";
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

// Java new String(sharedSecret, ISO_8859_1) => each byte maps to same code point 0..255.
// We'll do the same by converting bytes -> string via charCodeAt in that range.
function bytesToIso88591String(bytes) {
    let s = "";
    for (let i = 0; i < bytes.length; i++) s += String.fromCharCode(bytes[i]);
    return s;
}

export class EC_X extends AsymmCrypto {
    getTypes() {
        return [AsymmCryptoType.EC_C25519];
    }

    async checkPubKeyPair(pubKeyPair, type) {
        try {
            if (
                !pubKeyPair ||
                !(pubKeyPair.encKey instanceof Uint8Array) ||
                !(pubKeyPair.sigKey instanceof Uint8Array)
            ) {
                return false;
            }

            const encPub = await crypto.subtle.importKey(
                "spki",
                pubKeyPair.encKey,
                { name: "X25519" },
                false,
                ["deriveKey", "deriveBits"]
            );

            const sigPub = await crypto.subtle.importKey(
                "spki",
                pubKeyPair.sigKey,
                { name: "Ed25519" },
                false,
                ["verify"]
            );

            const dummy = await crypto.subtle.generateKey(
                { name: "X25519" },
                false,
                ["deriveBits"]
            );
            await crypto.subtle.deriveBits({ name: "X25519", public: encPub }, dummy.privateKey, 8);

            // If we got here, both imports succeeded.
            void sigPub;

            return true;
        } catch {
            return false;
        }
    }

    async generateKeypair(type) {
        try {
            const encPair = await crypto.subtle.generateKey(
                { name: "X25519" },
                true,
                ["deriveKey"]
            );
            const sigPair = await crypto.subtle.generateKey(
                { name: "Ed25519" },
                true,
                ["sign", "verify"]
            );

            const encPubSpki = new Uint8Array(await crypto.subtle.exportKey("spki", encPair.publicKey));
            const encPrivPkcs8 = new Uint8Array(await crypto.subtle.exportKey("pkcs8", encPair.privateKey));

            const sigPubSpki = new Uint8Array(await crypto.subtle.exportKey("spki", sigPair.publicKey));
            const sigPrivPkcs8 = new Uint8Array(await crypto.subtle.exportKey("pkcs8", sigPair.privateKey));

            return await AsymmFullKeyPair.fromParts(
                sigPubSpki, // pubSigKey
                encPubSpki, // pubEncKey
                sigPrivPkcs8, // privSigKey
                encPrivPkcs8, // privEncKey
                this,
                type
            );
        } catch (e) {
            throw e;
        }
    }

    async _importX25519PublicFromSpki(spkiBytes) {
        return await crypto.subtle.importKey(
            "spki",
            spkiBytes,
            { name: "X25519" },
            false,
            []
        );
    }

    async _importX25519PrivateFromPkcs8(pkcs8Bytes) {
        return await crypto.subtle.importKey(
            "pkcs8",
            pkcs8Bytes,
            { name: "X25519" },
            false,
            ["deriveBits"]
        );
    }

    async _deriveX25519SharedSecretBytes(privKey, pubKey) {
        // In X25519, deriveBits can output raw shared secret.
        // X25519 shared secret length is typically 32 bytes; request 256 bits.
        const bits = await crypto.subtle.deriveBits({ name: "X25519", public: pubKey }, privKey, 256);
        return new Uint8Array(bits);
    }

    async encrypt(dataBytes, pubEncKeySpki, type) {
        try {
            // recipient public key (X25519) in SPKI
            const recipientPub = await this._importX25519PublicFromSpki(pubEncKeySpki);

            // sender ephemeral X25519 keypair
            const eph = await crypto.subtle.generateKey(
                { name: "X25519" },
                true,
                ["deriveBits"]
            );

            // derive shared secret: ECDH(ephPrivate, recipientPublic)
            const sharedSecret = await this._deriveX25519SharedSecretBytes(eph.privateKey, recipientPub);

            // Java: crypto.encryptRaw(data, new String(sharedSecret, ISO_8859_1))
            const sharedSecretStr = bytesToIso88591String(sharedSecret);
            const res = await cryptoObj.encryptRaw(dataBytes, sharedSecretStr);

            // export ephemeral public key
            const ephPubSpki = new Uint8Array(await crypto.subtle.exportKey("spki", eph.publicKey));

            if (ephPubSpki.length > 0xffff) {
                throw new Error("Ephemeral public key too large for short length: " + ephPubSpki.length);
            }

            // Java output: [2 bytes ephPubLen] [ephPub] [res]
            const out = new Uint8Array(2 + ephPubSpki.length + res.length);
            setUint16BE(out, 0, ephPubSpki.length);
            out.set(ephPubSpki, 2);
            out.set(res, 2 + ephPubSpki.length);
            return out;
        } catch (e) {
            throw e;
        }
    }

    async decrypt(dataBytes, privEncKeyPkcs8, type) {
        try {
            if (dataBytes.length < 2) throw new Error("Invalid input length");

            const ephPubLen = getUint16BE(dataBytes, 0);

            if (dataBytes.length < 2 + ephPubLen) {
                throw new Error("Invalid input length for eph public key: " + dataBytes.length);
            }

            const ephPubSpki = dataBytes.slice(2, 2 + ephPubLen);
            const resOff = 2 + ephPubLen;
            const resLen = dataBytes.length - resOff;
            const res = dataBytes.slice(resOff, resOff + resLen);

            const recipientPriv = await this._importX25519PrivateFromPkcs8(privEncKeyPkcs8);
            const ephPub = await this._importX25519PublicFromSpki(ephPubSpki);

            // derive shared secret: ECDH(recipientPrivate, ephPublic)
            const sharedSecret = await this._deriveX25519SharedSecretBytes(recipientPriv, ephPub);

            const sharedSecretStr = bytesToIso88591String(sharedSecret);

            // Java: crypto.decryptRaw(res, new String(sharedSecret, ISO_8859_1))
            return cryptoObj.decryptRaw(res, sharedSecretStr);
        } catch (e) {
            throw e;
        }
    }

    async sign(dataBytes, privSigKeyPkcs8, type) {
        try {
            const privKey = await crypto.subtle.importKey(
                "pkcs8",
                privSigKeyPkcs8,
                { name: "Ed25519" },
                false,
                ["sign"]
            );
            const sig = await crypto.subtle.sign({ name: "Ed25519" }, privKey, dataBytes);
            return new Uint8Array(sig);
        } catch (e) {
            throw e;
        }
    }

    async verify(dataBytes, sigBytes, pubSigKeySpki, type) {
        try {
            const pubKey = await crypto.subtle.importKey(
                "spki",
                pubSigKeySpki,
                { name: "Ed25519" },
                false,
                ["verify"]
            );
            return await crypto.subtle.verify({ name: "Ed25519" }, pubKey, sigBytes, dataBytes);
        } catch (e) {
            throw e;
        }
    }
}