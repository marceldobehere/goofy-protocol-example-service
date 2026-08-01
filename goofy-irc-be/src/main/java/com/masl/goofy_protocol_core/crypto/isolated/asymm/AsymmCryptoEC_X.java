package com.masl.goofy_protocol_core.crypto.isolated.asymm;

import com.masl.goofy_protocol_core.crypto.isolated.symm.GlobSymmCrypto;

import javax.crypto.KeyAgreement;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.*;
import java.util.List;

public class AsymmCryptoEC_X implements AsymmCrypto {
    private final static GlobSymmCrypto crypto = new GlobSymmCrypto();

    @Override
    public List<AsymmCryptoType> getTypes() {
        return List.of(AsymmCryptoType.EC_C25519);
    }

    private static String keySpecEnc(AsymmCryptoType type) {
        return switch (type) {
            case EC_C25519 -> "X25519";
            default -> throw new IllegalArgumentException("Invalid type");
        };
    }

    private static String keySpecSig(AsymmCryptoType type) {
        return switch (type) {
            case EC_C25519 -> "Ed25519";
            default -> throw new IllegalArgumentException("Invalid type");
        };
    }

    @Override
    public boolean checkPubKeyPair(AsymmPubKeyPair pubKeyPair, AsymmCryptoType type) {
        try {
            KeyFactory kf = KeyFactory.getInstance(keySpecEnc(type), "BC");
            kf.generatePublic(new X509EncodedKeySpec(pubKeyPair.encKey()));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | NoSuchProviderException e) {
            return false;
        }

        try {
            KeyFactory kf = KeyFactory.getInstance(keySpecSig(type), "BC");
            PublicKey publicKey = kf.generatePublic(new X509EncodedKeySpec(pubKeyPair.sigKey()));
            Signature signature = Signature.getInstance(keySpecSig(type), "BC");
            signature.initVerify(publicKey);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | NoSuchProviderException e) {
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
            encGen = KeyPairGenerator.getInstance(keySpecEnc(type), "BC");
            sigGen = KeyPairGenerator.getInstance(keySpecSig(type), "BC");
        } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
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
            // X25519 public key
            KeyFactory kf = KeyFactory.getInstance(keySpecEnc(type), "BC");
            PublicKey publicKey = kf.generatePublic(new X509EncodedKeySpec(pubEncKey));

            // Ephemeral ECDH (sender side) key agreement
            KeyPairGenerator encGen = KeyPairGenerator.getInstance(keySpecEnc(type), "BC");
            KeyPair eph = encGen.generateKeyPair();

            // Derive shared secret: ECDH(ephemeralPrivate, recipientPublic)
            KeyAgreement ka = KeyAgreement.getInstance(keySpecEnc(type), "BC");
            ka.init(eph.getPrivate());
            ka.doPhase(publicKey, true);
            byte[] senderSharedSecret = ka.generateSecret();

            // AES-encrypted result using derived shared secret
            byte[] res = crypto.encryptRaw(data, new String(senderSharedSecret, StandardCharsets.ISO_8859_1));
            byte[] ephPub = eph.getPublic().getEncoded();

            // calc size
            if (ephPub.length > 0xFFFF)
                throw new IllegalArgumentException("Ephemeral public key too large for short length: " + ephPub.length);

            int totalLen = 2 + ephPub.length + res.length;
            byte[] out = new byte[totalLen];

            // first two bytes: size of eph public key as a short (big-endian)
            out[0] = (byte) ((ephPub.length >>> 8) & 0xFF);
            out[1] = (byte) (ephPub.length & 0xFF);

            // then eph public key
            System.arraycopy(ephPub, 0, out, 2, ephPub.length);

            // then AES-encrypted result
            System.arraycopy(res, 0, out, 2 + ephPub.length, res.length);

            return out;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public byte[] decrypt(byte[] data, byte[] privEncKey, AsymmCryptoType type) {
        try {
            // X25519 private key
            KeyFactory kf = KeyFactory.getInstance(keySpecEnc(type), "BC");
            PrivateKey privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(privEncKey));

            // first two bytes: eph public key length as a short (big-endian)
            int ephPubLen = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);

            // then eph public key
            if (data.length < 2 + ephPubLen)
                throw new IllegalArgumentException("Invalid input length for eph public key: " + data.length);

            byte[] ephPub = new byte[ephPubLen];
            System.arraycopy(data, 2, ephPub, 0, ephPubLen);

            // then AES-encrypted result
            int resOff = 2 + ephPubLen;
            int resLen = data.length - resOff;
            byte[] res = new byte[resLen];
            System.arraycopy(data, resOff, res, 0, resLen);

            // rebuild eph public key
            PublicKey ephPublicKey = kf.generatePublic(new X509EncodedKeySpec(ephPub));

            // Derive shared secret: ECDH(recipientPrivate, ephPublic)
            KeyAgreement ka = KeyAgreement.getInstance(keySpecEnc(type), "BC");
            ka.init(privateKey);
            ka.doPhase(ephPublicKey, true);
            byte[] senderSharedSecret = ka.generateSecret();

            // AES-decrypt using derived shared secret
            return crypto.decryptRaw(res, new String(senderSharedSecret, StandardCharsets.ISO_8859_1));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public byte[] sign(byte[] data, byte[] privSigKey, AsymmCryptoType type) {
        try {
            KeyFactory kf = KeyFactory.getInstance(keySpecSig(type), "BC");
            PrivateKey privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(privSigKey));
            Signature signature = Signature.getInstance(keySpecSig(type), "BC");
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
            KeyFactory kf = KeyFactory.getInstance(keySpecSig(type), "BC");
            PublicKey publicKey = kf.generatePublic(new X509EncodedKeySpec(pubSigKey));
            Signature signature = Signature.getInstance(keySpecSig(type));
            signature.initVerify(publicKey);
            signature.update(data);
            return signature.verify(sig);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
