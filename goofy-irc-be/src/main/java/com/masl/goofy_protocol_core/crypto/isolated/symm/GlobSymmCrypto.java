package com.masl.goofy_protocol_core.crypto.isolated.symm;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;

import static com.masl.goofy_protocol_core.crypto.isolated.SecretUtils.ENC_DELIMITER;

public class GlobSymmCrypto {
    final List<SymmCrypto> cryptoList = List.of(new SymmCryptoAES(), new SymmCryptoChaCha20());

    public List<SymmCryptoType> getTypes() {
        return cryptoList.stream().map(SymmCrypto::getTypes).flatMap(List::stream).toList();
    }

    public SymmCrypto forType(SymmCryptoType type) {
        return cryptoList.stream().filter(c -> c.getTypes().contains(type)).findFirst().orElse(null);
    }

    public record ParsedEncData(byte[] data, SymmCryptoType type) {
        public static ParsedEncData parse(String value) {
            String[] parts = value.split(Pattern.quote(ENC_DELIMITER));
            if (parts.length != 2)
                throw new IllegalArgumentException("Invalid data format");
            SymmCryptoType type = SymmCryptoType.valueOf(parts[0]);
            byte[] data = Base64.getUrlDecoder().decode(parts[1]);
            return new ParsedEncData(data, type);
        }

        public String serialize() {
            return type.toString() + ENC_DELIMITER + Base64.getUrlEncoder().encodeToString(data);
        }
    }

    // Encrypt String into Parsed Data in Base64 Format
    public String encryptStr(String data, String secret, SymmCryptoType type) {
        return encrypt(data.getBytes(StandardCharsets.UTF_8), secret, type);
    }

    // Decrypt Parsed Data in Base64 Format into String
    public String decryptStr(String data, String secret) {
        return decrypt(data, secret) != null ? new String(decrypt(data, secret), StandardCharsets.UTF_8) : null;
    }

    // Encrypt Byte Array into Parsed Data in Base64 Format
    public String encrypt(byte[] data, String secret, SymmCryptoType type) {
        SymmCrypto crypto = forType(type);
        if (crypto == null || data == null || secret == null)
            throw new IllegalArgumentException("Invalid type, data or secret");
        byte[] res = crypto.encrypt(data, crypto.fromSecretString(secret, type), type);
        return new ParsedEncData(res, type).serialize();
    }

    // Decrypt Parsed Data in Base64 Format into Byte Array
    public byte[] decrypt(String data, String secret) {
        ParsedEncData parsed = ParsedEncData.parse(data);
        SymmCrypto crypto = forType(parsed.type());
        if (crypto == null || secret == null)
            throw new IllegalArgumentException("Invalid type or secret");
        return crypto.decrypt(parsed.data, crypto.fromSecretString(secret, parsed.type()), parsed.type());
    }

    // Encrypt Raw Byte Array into Byte Array (+ Crypto Type)
    public byte[] encryptRaw(byte[] data, String secret, SymmCryptoType type) {
        SymmCrypto crypto = forType(type);
        if (crypto == null || data == null || secret == null)
            throw new IllegalArgumentException("Invalid type, data or secret");
        byte[] res = crypto.encrypt(data, crypto.fromSecretString(secret, type), type);

        ByteBuffer buf = ByteBuffer.allocate(2 + res.length);
        buf.putShort(type.getValue());
        buf.put(res);
        return buf.array();
    }

    // Decrypt Raw Byte Array into Byte Array
    public byte[] decryptRaw(byte[] data, String secret) {
        if (data == null || secret == null || data.length < 2)
            throw new IllegalArgumentException("Invalid type or secret");

        ByteBuffer buf = ByteBuffer.wrap(data);
        SymmCryptoType type = SymmCryptoType.fromValue(buf.getShort());

        SymmCrypto crypto = forType(type);
        if (crypto == null)
            throw new IllegalArgumentException("Invalid type or secret");

        byte[] enc = new byte[buf.remaining()];
        buf.get(enc);

        return crypto.decrypt(enc, crypto.fromSecretString(secret, type), type);
    }


    // Default Methods using the Default Symmetric Crypto Algo
    public static final SymmCryptoType DEFAULT_TYPE = SymmCryptoType.AES_GCM_256;
    public byte[] encryptRaw(byte[] data, String secret) {return encryptRaw(data, secret, DEFAULT_TYPE);}
    public String encrypt(byte[] data, String secret) {return encrypt(data, secret, DEFAULT_TYPE);}
    public String encryptStr(String data, String secret) {return encryptStr(data, secret, DEFAULT_TYPE);}
}
