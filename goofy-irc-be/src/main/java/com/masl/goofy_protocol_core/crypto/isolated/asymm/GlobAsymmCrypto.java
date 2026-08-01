package com.masl.goofy_protocol_core.crypto.isolated.asymm;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

public class GlobAsymmCrypto {
    final List<AsymmCrypto> cryptoList = List.of(new AsymmCryptoRSA(), new AsymmCryptoEC_P(), new AsymmCryptoEC_X(), new AsymmCryptoMLKEMDSA());

    public List<AsymmCryptoType> getTypes() {
        return cryptoList.stream().map(AsymmCrypto::getTypes).flatMap(List::stream).toList();
    }

    public AsymmCrypto forType(AsymmCryptoType type) {
        return cryptoList.stream().filter(c -> c.getTypes().contains(type)).findFirst().orElse(null);
    }

    public boolean checkPublicSplitKey(String pubSplitKey) {
        AsymmCrypto.AsymmPubKeyPair parsed = AsymmCrypto.AsymmPubKeyPair.parse(pubSplitKey);
        AsymmCrypto crypto = forType(parsed.type());
        if (crypto == null)
            throw new IllegalArgumentException("Invalid type");
        if (!crypto.checkPubKeyPair(parsed, parsed.type()))
            return false;
        return parsed.isSigValid(crypto);
    }

    public AsymmCrypto.AsymmFullKeyPair generateKeypair(AsymmCryptoType type) {
        AsymmCrypto crypto = forType(type);
        if (crypto == null)
            throw new IllegalArgumentException("Invalid type");
        return crypto.generateKeypair(type);
    }


    // Encrypt Raw Byte Array into Byte Array
    public byte[] encryptRaw(byte[] data, String pubSplitKey) {
        AsymmCrypto.AsymmPubKeyPair parsed = AsymmCrypto.AsymmPubKeyPair.parse(pubSplitKey);
        AsymmCrypto crypto = forType(parsed.type());
        if (crypto == null)
            throw new IllegalArgumentException("Invalid type");
        if (!parsed.isSigValid(crypto))
            throw new IllegalArgumentException("Public Key Sig is not valid");

        return crypto.encrypt(data, parsed.encKey(), parsed.type());
    }

    // Decrypt Raw Byte Array into Byte Array
    public byte[] decryptRaw(byte[] data, String privSplitKey) {
        AsymmCrypto.AsymmPrivKeyPair parsed = AsymmCrypto.AsymmPrivKeyPair.parse(privSplitKey);
        AsymmCrypto crypto = forType(parsed.type());
        if (crypto == null)
            throw new IllegalArgumentException("Invalid type");

        return crypto.decrypt(data, parsed.encKey(), parsed.type());
    }

    // Encrypt Raw Byte Array into Base64 String
    public String encrypt(byte[] data, String pubSplitKey) {
        return Base64.getUrlEncoder().encodeToString(encryptRaw(data, pubSplitKey));
    }

    // Decrypt Base64 String into Byte Array
    public byte[] decrypt(String data, String privSplitKey) {
        return decryptRaw(Base64.getUrlDecoder().decode(data), privSplitKey);

    }

    // Encrypt String into Base64 String
    public String encryptStr(String data, String pubSplitKey) {
        return encrypt(data.getBytes(StandardCharsets.UTF_8), pubSplitKey);
    }

    // Decrypt Base64 String into String
    public String decryptStr(String data, String privSplitKey) {
        return new String(decrypt(data, privSplitKey), StandardCharsets.UTF_8);
    }

    // Create a Raw Byte Array Signature for Data in a Byte Array
    public byte[] signRaw(byte[] data, String privSplitKey) {
        AsymmCrypto.AsymmPrivKeyPair parsed = AsymmCrypto.AsymmPrivKeyPair.parse(privSplitKey);
        AsymmCrypto crypto = forType(parsed.type());
        if (crypto == null)
            throw new IllegalArgumentException("Invalid type");

        return crypto.sign(data, parsed.sigKey(), parsed.type());
    }

    // Verify a Raw Byte Array Signature for Data in a Byte Array
    public boolean verifyRaw(byte[] data, byte[] sig, String pubSplitKey) {
        AsymmCrypto.AsymmPubKeyPair parsed = AsymmCrypto.AsymmPubKeyPair.parse(pubSplitKey);
        AsymmCrypto crypto = forType(parsed.type());
        if (crypto == null)
            throw new IllegalArgumentException("Invalid type");

        return crypto.verify(data, sig, parsed.sigKey(), parsed.type());
    }

    // Create a Base64 String Signature for Data in a Byte Array
    public String sign(byte[] data, String privSplitKey) {
        return Base64.getUrlEncoder().encodeToString(signRaw(data, privSplitKey));
    }

    // Verify a Base64 String Signature for Data in a Byte Array
    public boolean verify(byte[] data, String sig, String pubSplitKey) {
        return verifyRaw(data, Base64.getUrlDecoder().decode(sig), pubSplitKey);
    }

    // Create a Base64 String Signature for Data in a String
    public String signStr(String data, String privSplitKey) {
        return sign(data.getBytes(StandardCharsets.UTF_8), privSplitKey);
    }

    // Verify a Base64 String Signature for Data in a String
    public boolean verifyStr(String data, String sig, String pubSplitKey) {
        return verify(data.getBytes(StandardCharsets.UTF_8), sig, pubSplitKey);
    }


    // Default Methods using the Default Asymmetric Crypto Algo
    public static final AsymmCryptoType DEFAULT_TYPE = AsymmCryptoType.EC_C25519;
    public AsymmCrypto.AsymmFullKeyPair generateKeypair() {return generateKeypair(DEFAULT_TYPE);}
}
