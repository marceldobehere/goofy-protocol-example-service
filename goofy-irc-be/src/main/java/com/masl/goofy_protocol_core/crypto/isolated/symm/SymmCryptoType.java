package com.masl.goofy_protocol_core.crypto.isolated.symm;

import lombok.Getter;

@Getter
public enum SymmCryptoType {
    AES_GCM_128((short)0x0100),
    AES_GCM_192((short)0x0101),
    AES_GCM_256((short)0x0102),
    CHACHA_20((short)0x0200);

    private final short value;
    SymmCryptoType(short value) {
        this.value = value;
    }

    public static SymmCryptoType fromValue(short value) {
        for (SymmCryptoType t : values())
            if (t.value == value)
                return t;
        throw new IllegalArgumentException("Unknown SymmCryptoType value: " + value);
    }
}
