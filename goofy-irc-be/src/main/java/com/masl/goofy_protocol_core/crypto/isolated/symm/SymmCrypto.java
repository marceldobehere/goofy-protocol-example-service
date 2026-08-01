package com.masl.goofy_protocol_core.crypto.isolated.symm;

import java.util.List;

public interface SymmCrypto {
    List<SymmCryptoType> getTypes();

    byte[] fromSecretString(String secret, SymmCryptoType type);
    byte[] encrypt(byte[] data, byte[] secret, SymmCryptoType type);
    byte[] decrypt(byte[] data, byte[] secret, SymmCryptoType type);
}
