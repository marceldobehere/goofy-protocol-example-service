export const SymmCryptoType = Object.freeze({
    AES_GCM_128: { name: "AES_GCM_128", value: 0x0100 },
    AES_GCM_192: { name: "AES_GCM_192", value: 0x0101 },
    AES_GCM_256: { name: "AES_GCM_256", value: 0x0102 },
    CHACHA_20: { name: "CHACHA_20", value: 0x0200 },

    fromValue(value) {
        const keys = Object.keys(this).filter(k => k !== "fromValue");
        for (const k of keys) {
            const t = this[k];
            if (t.value === value) return t;
        }
        throw new Error(`Unknown SymmCryptoType value: ${value}`);
    }
});