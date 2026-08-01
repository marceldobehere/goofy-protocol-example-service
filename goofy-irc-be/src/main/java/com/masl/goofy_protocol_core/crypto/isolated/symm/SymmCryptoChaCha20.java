package com.masl.goofy_protocol_core.crypto.isolated.symm;

import com.masl.goofy_protocol_core.crypto.isolated.SecretUtils;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.ChaCha20ParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.List;

public class SymmCryptoChaCha20 implements SymmCrypto {
    @Override
    public List<SymmCryptoType> getTypes() {
        return List.of(SymmCryptoType.CHACHA_20);
    }

    @Override
    public byte[] fromSecretString(String secretStr, SymmCryptoType type) {
        try {
            byte[] secretSpecBytes = SecretUtils.symmSecretFromSecret(secretStr, SecretUtils.DEFAULT_DETERMINISTIC_SALT, keySize(type));
            SecretKey secret = new SecretKeySpec(secretSpecBytes, "ChaCha20");
            return secret.getEncoded();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static int keySize(SymmCryptoType type) {
        return switch (type) {
            case CHACHA_20 -> 256;
            default -> throw new IllegalArgumentException("Invalid type");
        };
    }

    @Override
    public byte[] encrypt(byte[] data, byte[] secret, SymmCryptoType type) {
        int keyBits = keySize(type);
        if (secret == null || secret.length * 8 != keyBits) {
            throw new IllegalArgumentException("Secret length must be " + (keyBits / 8) + " bytes for " + type);
        }

        try {
            SecretKey secretKey = new SecretKeySpec(secret, "ChaCha20");
            byte[] nonce = new byte[12];
            new SecureRandom().nextBytes(nonce);
            int counter = new SecureRandom().nextInt();

            Cipher cipher = Cipher.getInstance("ChaCha20");
            ChaCha20ParameterSpec param = new ChaCha20ParameterSpec(nonce, counter);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, param);
            byte[] encryptedText = cipher.doFinal(data);

            // append nonce + count
            byte[] output = new byte[encryptedText.length + nonce.length + 4];

            System.arraycopy(encryptedText, 0, output, 0, encryptedText.length);
            System.arraycopy(nonce, 0, output, encryptedText.length, nonce.length);

            // convert int to byte[]
            byte[] counterByteArray = ByteBuffer.allocate(4).putInt(counter).array();
            System.arraycopy(counterByteArray, 0, output, encryptedText.length + nonce.length, 4);

            return output;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public byte[] decrypt(byte[] data, byte[] secret, SymmCryptoType type) {
        int keyBits = keySize(type);
        if (secret == null || secret.length * 8 != keyBits) {
            throw new IllegalArgumentException("Secret length must be " + (keyBits / 8) + " bytes for " + type);
        }

        try {
            SecretKey secretKey = new SecretKeySpec(secret, "ChaCha20");
            byte[] nonce = new byte[12];
            byte[] counter = new byte[4];

            // get only the encrypted text
            byte[] encryptedText = new byte[data.length - (nonce.length + counter.length)];
            System.arraycopy(data, 0, encryptedText, 0, data.length - (nonce.length + counter.length));

            // get nonce and counter
            System.arraycopy(data, encryptedText.length, nonce, 0, nonce.length);
            System.arraycopy(data, encryptedText.length + nonce.length, counter, 0, counter.length);

            // convert byte array to int
            int ic = ByteBuffer.wrap(counter).getInt();
            ChaCha20ParameterSpec param = new ChaCha20ParameterSpec(nonce, ic);
            Cipher cipher = Cipher.getInstance("ChaCha20");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, param);

            return cipher.doFinal(encryptedText);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
