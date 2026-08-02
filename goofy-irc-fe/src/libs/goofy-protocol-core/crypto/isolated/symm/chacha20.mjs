
import { DEFAULT_DETERMINISTIC_SALT, symmSecretFromSecret } from "../secret-utils.mjs";
import { SymmCryptoType } from "./symm-crypto-type.mjs";

export class ChaCha20 {
    getTypes() {
        return [SymmCryptoType.CHACHA_20];
    }

    keySizeBits(type) {
        switch (type) {
            case SymmCryptoType.CHACHA_20:
                return 256;
            default:
                throw new Error("Invalid type");
        }
    }

    async fromSecretString(secretStr, type) {
        const keyBits = this.keySizeBits(type);
        const keyBytes = await symmSecretFromSecret(
            secretStr,
            DEFAULT_DETERMINISTIC_SALT,
            keyBits
        );
        return keyBytes; // Uint8Array
    }

    async encrypt(dataBytes, secretBytes, type) {
        const keyBits = this.keySizeBits(type);
        if (!secretBytes || secretBytes.length * 8 !== keyBits) {
            throw new Error(`Secret length must be ${keyBits / 8} bytes for ${type}`);
        }

        // Java: nonce[12] + counter(int) where counter is random int
        const nonce = new Uint8Array(12);
        crypto.getRandomValues(nonce);

        // Java's new SecureRandom().nextInt() -> 32-bit signed int
        const counter = (crypto.getRandomValues(new Uint32Array(1))[0] | 0);

        const cipherText = await chacha20EncryptRaw(
            dataBytes,
            secretBytes,
            nonce,
            counter
        );

        // output = encryptedText || nonce || counter(4 bytes)
        const out = new Uint8Array(cipherText.length + nonce.length + 4);
        out.set(cipherText, 0);
        out.set(nonce, cipherText.length);

        const counterBytes = new Uint8Array(4);
        // Java ByteBuffer.allocate(4).putInt(counter).array() is big-endian
        new DataView(counterBytes.buffer).setInt32(0, counter, false);
        out.set(counterBytes, cipherText.length + nonce.length);

        return out;
    }

    async decrypt(dataBytes, secretBytes, type) {
        const keyBits = this.keySizeBits(type);
        if (!secretBytes || secretBytes.length * 8 !== keyBits) {
            throw new Error(`Secret length must be ${keyBits / 8} bytes for ${type}`);
        }

        const nonceLen = 12;
        const counterLen = 4;
        if (!dataBytes || dataBytes.length < nonceLen + counterLen) {
            throw new Error("Ciphertext too short");
        }

        const encryptedTextLen = dataBytes.length - (nonceLen + counterLen);
        const encryptedText = dataBytes.slice(0, encryptedTextLen);

        const nonce = dataBytes.slice(encryptedTextLen, encryptedTextLen + nonceLen);

        const counterBytes = dataBytes.slice(encryptedTextLen + nonceLen, dataBytes.length);
        const ic = new DataView(counterBytes.buffer, counterBytes.byteOffset, 4).getInt32(0, false);

        return chacha20EncryptRaw(encryptedText, secretBytes, nonce, ic); // symmetric
    }
}

// -------- ChaCha20 raw (no Poly1305) --------
// Implements ChaCha20(key[32], nonce[12], counter[int32]) as used by Java Cipher "ChaCha20".
async function chacha20EncryptRaw(plaintextBytes, keyBytes32, nonce12, counterInt32) {
    if (!(keyBytes32 instanceof Uint8Array) || keyBytes32.length !== 32) {
        throw new Error("Key must be 32 bytes");
    }
    if (!(nonce12 instanceof Uint8Array) || nonce12.length !== 12) {
        throw new Error("Nonce must be 12 bytes");
    }

    const pt = plaintextBytes instanceof Uint8Array ? plaintextBytes : new Uint8Array(plaintextBytes);
    const out = new Uint8Array(pt.length);

    // ChaCha20 uses 16-word (512-bit) state:
    // constants (4), key (8), counter (1), nonce (3)
    // Java's ChaCha20ParameterSpec(nonce, counter) maps counter to the 32-bit block counter word.
    const state = new Uint32Array(16);
    state[0] = 0x61707865;
    state[1] = 0x3320646e;
    state[2] = 0x79622d32;
    state[3] = 0x6b206574;

    // key -> 8 words little-endian
    for (let i = 0; i < 8; i++) {
        state[4 + i] = getU32LE(keyBytes32, i * 4);
    }

    state[12] = counterInt32 >>> 0; // counter as unsigned 32-bit for arithmetic
    state[13] = getU32LE(nonce12, 0);
    state[14] = getU32LE(nonce12, 4);
    state[15] = getU32LE(nonce12, 8);

    let block = 0;
    let offset = 0;

    while (offset < pt.length) {
        const working = new Uint32Array(state);
        chacha20Block(working, 20);

        // keystream block = 64 bytes (16 words little-endian)
        for (let i = 0; i < 16 && offset < pt.length; i++) {
            const ksWord = working[i];
            // bytes for this word
            for (let b = 0; b < 4 && offset < pt.length; b++) {
                out[offset] = pt[offset] ^ ((ksWord >>> (8 * b)) & 0xff);
                offset++;
            }
        }

        // increment block counter (32-bit)
        state[12] = (state[12] + 1) >>> 0;
        block++;
    }

    return out;
}

function rotl(x, n) {
    return ((x << n) | (x >>> (32 - n))) >>> 0;
}

function quarterRound(x, a, b, c, d) {
    x[a] = (x[a] + x[b]) >>> 0;
    x[d] ^= x[a];
    x[d] = rotl(x[d], 16);

    x[c] = (x[c] + x[d]) >>> 0;
    x[b] ^= x[c];
    x[b] = rotl(x[b], 12);

    x[a] = (x[a] + x[b]) >>> 0;
    x[d] ^= x[a];
    x[d] = rotl(x[d], 8);

    x[c] = (x[c] + x[d]) >>> 0;
    x[b] ^= x[c];
    x[b] = rotl(x[b], 7);
}

function chacha20Block(state, rounds) {
    // state is 16 words; operates in-place on a working copy
    // 20 rounds = 10 column+diagonal pairs
    for (let i = 0; i < rounds; i += 2) {
        // column rounds
        quarterRound(state, 0, 4, 8, 12);
        quarterRound(state, 1, 5, 9, 13);
        quarterRound(state, 2, 6, 10, 14);
        quarterRound(state, 3, 7, 11, 15);
        // diagonal rounds
        quarterRound(state, 0, 5, 10, 15);
        quarterRound(state, 1, 6, 11, 12);
        quarterRound(state, 2, 7, 8, 13);
        quarterRound(state, 3, 4, 9, 14);
    }
}

function getU32LE(bytes, off) {
    return (
        (bytes[off] & 0xff) |
        ((bytes[off + 1] & 0xff) << 8) |
        ((bytes[off + 2] & 0xff) << 16) |
        ((bytes[off + 3] & 0xff) << 24)
    ) >>> 0;
}