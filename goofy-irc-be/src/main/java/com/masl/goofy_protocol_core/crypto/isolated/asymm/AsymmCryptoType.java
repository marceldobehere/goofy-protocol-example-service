package com.masl.goofy_protocol_core.crypto.isolated.asymm;

import lombok.Getter;

@Getter
public enum AsymmCryptoType {
    // Using RSA/ECB/OAEPWithSHA-256AndMGF1Padding & RSASSA-PKCS1-v1_5 + SHA-256
    RSA_2048((short)0x0100),
    RSA_3072((short)0x0101),
    RSA_4096((short)0x0102),

    // Using P-256/384
    EC_P256((short)0x0200),
    EC_P384((short)0x0201),
    // Using Curve25519
    EC_C25519((short)0x0210),

    MLKEMDSA_512_44((short)0x0300),
    MLKEMDSA_768_65((short)0x0301),
    MLKEMDSA_1024_87((short)0x0302);

    private final short value;
    AsymmCryptoType(short value) {
        this.value = value;
    }

    public static AsymmCryptoType fromValue(short value) {
        for (AsymmCryptoType t : values())
            if (t.value == value)
                return t;
        throw new IllegalArgumentException("Unknown AsymmCryptoType value: " + value);
    }
}