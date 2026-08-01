package com.masl.goofy_protocol_core.crypto.isolated.asymm;

import com.masl.goofy_protocol_core.crypto.isolated.symm.GlobSymmCrypto;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.nio.ByteBuffer;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.List;

public class AsymmCryptoRSA implements AsymmCrypto {
    private final static GlobSymmCrypto crypto = new GlobSymmCrypto();

    @Override
    public List<AsymmCryptoType> getTypes() {
        return List.of(AsymmCryptoType.RSA_2048, AsymmCryptoType.RSA_3072, AsymmCryptoType.RSA_4096);
    }

    private static int keySize(AsymmCryptoType type) {
        return switch (type) {
            case RSA_2048 -> 2048;
            case RSA_3072 -> 3072;
            case RSA_4096 -> 4096;
            default -> throw new IllegalArgumentException("Invalid type");
        };
    }

    private static int maxContentSize(AsymmCryptoType type) {
        int k = keySize(type) / 8;      // modulus length in bytes
        int hLen = 32;                 // SHA-256 output length in bytes
        return k - 2 * hLen - 2 - 10;      // OAEP max message size
    }

    @Override
    public boolean checkPubKeyPair(AsymmPubKeyPair pubKeyPair, AsymmCryptoType type) {
        for (var pubKey : List.of(pubKeyPair.encKey(), pubKeyPair.sigKey()))
            try {
                KeyFactory kf = KeyFactory.getInstance("RSA");
                PublicKey publicKey = kf.generatePublic(new X509EncodedKeySpec(pubKey));
                Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
                OAEPParameterSpec spec = new OAEPParameterSpec(
                        "SHA-256",
                        "MGF1",
                        MGF1ParameterSpec.SHA256,
                        PSource.PSpecified.DEFAULT
                );
                cipher.init(Cipher.ENCRYPT_MODE, publicKey, spec);
            } catch (NoSuchAlgorithmException | InvalidKeySpecException | NoSuchPaddingException | InvalidKeyException |
                     InvalidAlgorithmParameterException e) {
                return false;
            }
        return true;
    }

    @Override
    public AsymmFullKeyPair generateKeypair(AsymmCryptoType type) {
        KeyPairGenerator generator;
        try {
            generator = KeyPairGenerator.getInstance("RSA");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        generator.initialize(keySize(type));
        KeyPair sigPair = generator.generateKeyPair();
        return AsymmFullKeyPair.fromParts(
                sigPair.getPublic().getEncoded(), sigPair.getPublic().getEncoded(),
                sigPair.getPrivate().getEncoded(), sigPair.getPrivate().getEncoded(),
                this, type
        );
    }

    public byte[] _encryptSmall(byte[] data, byte[] pubEncKey) {
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            PublicKey publicKey = kf.generatePublic(new X509EncodedKeySpec(pubEncKey));
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
            OAEPParameterSpec spec = new OAEPParameterSpec(
                    "SHA-256",
                    "MGF1",
                    MGF1ParameterSpec.SHA256,
                    PSource.PSpecified.DEFAULT
            );
            cipher.init(Cipher.ENCRYPT_MODE, publicKey, spec);
            return cipher.doFinal(data);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static final int SYMM_KEY_SIZE = 32;
    public byte[] _encryptBig(byte[] data, byte[] pubEncKey) {
        try {
            // Create Symm Key
            byte[] symmKey = new byte[SYMM_KEY_SIZE];
            new SecureRandom().nextBytes(symmKey);

            // Encrypt Data with Symm Key
            byte[] dataEnc = crypto.encryptRaw(data, new String(symmKey, java.nio.charset.StandardCharsets.ISO_8859_1));

            // Encrypt the symmetric key with RSA
            byte[] symmKeyEnc = _encryptSmall(symmKey, pubEncKey);

            // Check Length
            if (symmKeyEnc.length > 0xFFFF)
                throw new IllegalArgumentException("Encrypted symmetric key too large for short length: " + symmKeyEnc.length);

            // Create Result Array
            int totalLen = 2 + symmKeyEnc.length + dataEnc.length;
            byte[] out = new byte[totalLen];

            out[0] = (byte) ((symmKeyEnc.length >>> 8) & 0xFF);
            out[1] = (byte) (symmKeyEnc.length & 0xFF);

            System.arraycopy(symmKeyEnc, 0, out, 2, symmKeyEnc.length);
            System.arraycopy(dataEnc, 0, out, 2 + symmKeyEnc.length, dataEnc.length);

            return out;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    @Override
    public byte[] encrypt(byte[] data, byte[] pubEncKey, AsymmCryptoType type) {
        boolean small = data.length <= maxContentSize(type);
        byte[] res = small ? _encryptSmall(data, pubEncKey) :_encryptBig(data, pubEncKey);

        ByteBuffer buf = ByteBuffer.allocate(1 + res.length);
        buf.put((byte)(small ? 1 : 0));
        buf.put(res);
        return buf.array();
    }

    public byte[] _decryptSmall(byte[] data, byte[] privEncKey) {
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            PrivateKey privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(privEncKey));
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
            OAEPParameterSpec spec = new OAEPParameterSpec(
                    "SHA-256",
                    "MGF1",
                    MGF1ParameterSpec.SHA256,
                    PSource.PSpecified.DEFAULT
            );
            cipher.init(Cipher.DECRYPT_MODE, privateKey, spec);
            return cipher.doFinal(data);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public byte[] _decryptBig(byte[] data, byte[] privEncKey) {
        try {
            if (data.length < 2)
                throw new IllegalArgumentException("Ciphertext too short.");

            // Get Symm Key Length
            int symmKeyEncLen = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);

            // Size Check
            if (data.length < 2 + symmKeyEncLen)
                throw new IllegalArgumentException("Ciphertext malformed (bad symmKey length).");

            byte[] symmKeyEnc = new byte[symmKeyEncLen];
            System.arraycopy(data, 2, symmKeyEnc, 0, symmKeyEncLen);

            byte[] dataEnc = new byte[data.length - (2 + symmKeyEncLen)];
            System.arraycopy(data, 2 + symmKeyEncLen, dataEnc, 0, dataEnc.length);

            // decrypt symmetric key with RSA
            byte[] symmKey = _decryptSmall(symmKeyEnc, privEncKey);

            // decrypt data with symm key
            return crypto.decryptRaw(dataEnc, new String(symmKey, java.nio.charset.StandardCharsets.ISO_8859_1));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public byte[] decrypt(byte[] data, byte[] privEncKey, AsymmCryptoType type) {
        if (data.length < 2)
            throw new IllegalArgumentException("Ciphertext too short.");
        boolean small = data[0] == 1;

        byte[] payload = new byte[data.length - 1];
        System.arraycopy(data, 1, payload, 0, payload.length);
        return small ? _decryptSmall(payload, privEncKey) : _decryptBig(payload, privEncKey);
    }

    @Override
    public byte[] sign(byte[] data, byte[] privSigKey, AsymmCryptoType type) {
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            PrivateKey privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(privSigKey));
            Signature privateSignature = Signature.getInstance("SHA256withRSA");
            privateSignature.initSign(privateKey);
            privateSignature.update(data);
            return privateSignature.sign();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean verify(byte[] data, byte[] sig, byte[] pubSigKey, AsymmCryptoType type) {
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            PublicKey publicKey = kf.generatePublic(new X509EncodedKeySpec(pubSigKey));
            Signature publicSignature = Signature.getInstance("SHA256withRSA");
            publicSignature.initVerify(publicKey);
            publicSignature.update(data);
            return publicSignature.verify(sig);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
