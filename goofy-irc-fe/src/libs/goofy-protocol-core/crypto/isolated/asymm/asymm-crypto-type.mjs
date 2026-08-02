export const AsymmCryptoType = Object.freeze({
    RSA_2048: { name: "RSA_2048", value: 0x0100 },
    RSA_3072: { name: "RSA_3072", value: 0x0101 },
    RSA_4096: { name: "RSA_4096", value: 0x0102 },

    EC_P256: { name: "EC_P256", value: 0x0200 },
    EC_P384: { name: "EC_P384", value: 0x0201 },

    EC_C25519: { name: "EC_C25519", value: 0x0210 },

    MLKEMDSA_512_44: { name: "MLKEMDSA_512_44", value: 0x0300 },
    MLKEMDSA_768_65: { name: "MLKEMDSA_768_65", value: 0x0301 },
    MLKEMDSA_1024_87: { name: "MLKEMDSA_1024_87", value: 0x0302 },

    fromValue(value) {
        const keys = Object.keys(this).filter(k => k !== "fromValue");
        for (const k of keys) {
            const t = this[k];
            if (t.value === value) return t;
        }
        throw new Error(`Unknown AsymmCryptoType value: ${value}`);
    },
});