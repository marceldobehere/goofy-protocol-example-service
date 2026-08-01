package com.masl.goofy_protocol_core.crypto.isolated.asymm;

import com.masl.goofy_protocol_core.crypto.isolated.symm.GlobSymmCrypto;

import javax.crypto.KEM;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.NamedParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.List;

// TODO: Look into storing public keys / Signatures in some kind of compressed format because they are CHONKY
public class AsymmCryptoMLKEMDSA implements AsymmCrypto {
    private final static GlobSymmCrypto crypto = new GlobSymmCrypto();

    @Override
    public List<AsymmCryptoType> getTypes() {
        return List.of(AsymmCryptoType.MLKEMDSA_512_44, AsymmCryptoType.MLKEMDSA_768_65, AsymmCryptoType.MLKEMDSA_1024_87);
    }

    private static NamedParameterSpec keySpecKem(AsymmCryptoType type) {
        return switch (type) {
            case MLKEMDSA_512_44 -> NamedParameterSpec.ML_KEM_512;
            case MLKEMDSA_768_65 -> NamedParameterSpec.ML_KEM_768;
            case MLKEMDSA_1024_87 -> NamedParameterSpec.ML_KEM_1024;
            default -> throw new IllegalArgumentException("Invalid type");
        };
    }

    private static NamedParameterSpec keySpecDsa(AsymmCryptoType type) {
        return switch (type) {
            case MLKEMDSA_512_44 -> NamedParameterSpec.ML_DSA_44;
            case MLKEMDSA_768_65 -> NamedParameterSpec.ML_DSA_65;
            case MLKEMDSA_1024_87 -> NamedParameterSpec.ML_DSA_87;
            default -> throw new IllegalArgumentException("Invalid type");
        };
    }

    @Override
    public boolean checkPubKeyPair(AsymmPubKeyPair pubKeyPair, AsymmCryptoType type) {
        try {
            KeyFactory kf = KeyFactory.getInstance("ML-KEM", "BC");
            kf.generatePublic(new X509EncodedKeySpec(pubKeyPair.encKey()));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | NoSuchProviderException e) {
            return false;
        }

        try {
            KeyFactory kf = KeyFactory.getInstance("ML-DSA", "BC");
            PublicKey publicKey = kf.generatePublic(new X509EncodedKeySpec(pubKeyPair.sigKey()));
            Signature signature = Signature.getInstance("ML-DSA");
            signature.initVerify(publicKey);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException  | NoSuchProviderException e) {
            return false;
        } catch (InvalidKeyException e) {
            throw new RuntimeException(e);
        }
        return true;
    }

    @Override
    public AsymmFullKeyPair generateKeypair(AsymmCryptoType type) {
        KeyPairGenerator encGen;
        KeyPairGenerator sigGen;
        try {
            encGen = KeyPairGenerator.getInstance("ML-KEM", "BC");
            encGen.initialize(keySpecKem(type));
            sigGen = KeyPairGenerator.getInstance("ML-DSA", "BC");
            sigGen.initialize(keySpecDsa(type));
        } catch (NoSuchAlgorithmException | NoSuchProviderException | InvalidAlgorithmParameterException e) {
            throw new RuntimeException(e);
        }
        KeyPair encPair = encGen.generateKeyPair();
        KeyPair sigPair = sigGen.generateKeyPair();

        return AsymmFullKeyPair.fromParts(
                sigPair.getPublic().getEncoded(), encPair.getPublic().getEncoded(),
                sigPair.getPrivate().getEncoded(), encPair.getPrivate().getEncoded(),
                this, type
        );
    }

    @Override
    public byte[] encrypt(byte[] data, byte[] pubEncKey, AsymmCryptoType type) {
        try {
            KeyFactory kf = KeyFactory.getInstance("ML-KEM", "BC");
            PublicKey publicKey = kf.generatePublic(new X509EncodedKeySpec(pubEncKey));

            KEM senderKem = KEM.getInstance("ML-KEM");
            KEM.Encapsulator encapsulator = senderKem.newEncapsulator(publicKey);
            KEM.Encapsulated encapsulated = encapsulator.encapsulate();

            byte[] senderSharedSecret = encapsulated.key().getEncoded();
            byte[] ciphertext = encapsulated.encapsulation();
            byte[] res = crypto.encryptRaw(data, new String(senderSharedSecret, StandardCharsets.ISO_8859_1));
            if (ciphertext.length > 0xFFFF)
                throw new IllegalArgumentException("Ciphertext too large for short length: " + ciphertext.length);

            // calc size
            int totalLen = 2 + ciphertext.length + res.length;
            byte[] out = new byte[totalLen];

            // first two bytes: size of ciphertext as a short (big-endian)
            out[0] = (byte) ((ciphertext.length >>> 8) & 0xFF);
            out[1] = (byte) (ciphertext.length & 0xFF);

            // then ciphertext
            System.arraycopy(ciphertext, 0, out, 2, ciphertext.length);

            // then AES-encrypted result
            System.arraycopy(res, 0, out, 2 + ciphertext.length, res.length);

            return out;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public byte[] decrypt(byte[] data, byte[] privEncKey, AsymmCryptoType type) {
        try {
            // read first two bytes (big-endian) = ciphertext length
            if (data.length < 2) throw new IllegalArgumentException("Input too short");
            int ctLen = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);

            if (2 + ctLen > data.length)
                throw new IllegalArgumentException("Invalid ciphertext length");

            byte[] ciphertext = new byte[ctLen];
            System.arraycopy(data, 2, ciphertext, 0, ctLen);

            byte[] encRes = new byte[data.length - (2 + ctLen)];
            System.arraycopy(data, 2 + ctLen, encRes, 0, encRes.length);

            // rebuild private key
            KeyFactory kf = KeyFactory.getInstance("ML-KEM", "BC");
            PrivateKey privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(privEncKey));

            // decapsulate to get shared secret
            KEM receiverKem = KEM.getInstance("ML-KEM");
            KEM.Decapsulator decapsulator = receiverKem.newDecapsulator(privateKey);
            byte[] receiverSharedSecret = decapsulator.decapsulate(ciphertext).getEncoded();

            // AES-GCM decrypt using shared secret
            return crypto.decryptRaw(encRes, new String(receiverSharedSecret, StandardCharsets.ISO_8859_1));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public byte[] sign(byte[] data, byte[] privSigKey, AsymmCryptoType type) {
        try {
            KeyFactory kf = KeyFactory.getInstance("ML-DSA", "BC");
            PrivateKey privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(privSigKey));
            Signature signature = Signature.getInstance("ML-DSA");
            signature.initSign(privateKey);
            signature.update(data);
            return signature.sign();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean verify(byte[] data, byte[] sig, byte[] pubSigKey, AsymmCryptoType type) {
        try {
            KeyFactory kf = KeyFactory.getInstance("ML-DSA", "BC");
            PublicKey publicKey = kf.generatePublic(new X509EncodedKeySpec(pubSigKey));
            Signature signature = Signature.getInstance("ML-DSA");
            signature.initVerify(publicKey);
            signature.update(data);
            return signature.verify(sig);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
