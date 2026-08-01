package com.masl.goofy_protocol_core.crypto.isolated.asymm;

import org.bouncycastle.jce.spec.IESParameterSpec;

import javax.crypto.Cipher;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.List;

public class AsymmCryptoEC_P implements AsymmCrypto {

    @Override
    public List<AsymmCryptoType> getTypes() {
        return List.of(AsymmCryptoType.EC_P256, AsymmCryptoType.EC_P384);
    }

    private static String keyAlgo(AsymmCryptoType type) {
        return switch (type) {
            case EC_P256 -> "secp256r1";
            case EC_P384 -> "secp384r1";
            default -> throw new IllegalArgumentException("Invalid type");
        };
    }

    @Override
    public boolean checkPubKeyPair(AsymmPubKeyPair pubKeyPair, AsymmCryptoType type) {
        for (var pubKey : List.of(pubKeyPair.encKey(), pubKeyPair.sigKey()))
            try {
                KeyFactory kf = KeyFactory.getInstance("EC", "BC");
                PublicKey publicKey = kf.generatePublic(new X509EncodedKeySpec(pubKey));
                Signature signature = Signature.getInstance("SHA256withECDSA");
                signature.initVerify(publicKey);
            } catch (NoSuchAlgorithmException | InvalidKeySpecException | InvalidKeyException | NoSuchProviderException e) {
                return false;
            }
        return true;
    }

    @Override
    public AsymmFullKeyPair generateKeypair(AsymmCryptoType type) {
        KeyPairGenerator generator;
        try {
            generator = KeyPairGenerator.getInstance("EC", "BC");
            generator.initialize(new ECGenParameterSpec(keyAlgo(type)));
        } catch (NoSuchAlgorithmException | NoSuchProviderException | InvalidAlgorithmParameterException e) {
            throw new RuntimeException(e);
        }
        KeyPair sigPair = generator.generateKeyPair();
        return AsymmFullKeyPair.fromParts(
                sigPair.getPublic().getEncoded(), sigPair.getPublic().getEncoded(),
                sigPair.getPrivate().getEncoded(), sigPair.getPrivate().getEncoded(),
                this, type
        );
    }

    // TODO: Either do stuff below, standardize or just remove in favor of X25519
    // Potentially incorporate a hash of the unencrypted data, so that the decryption can validate it has not been tampered with
    // Look into if "ECIESwithAES-CBC" is standardized / easily portable?
    @Override
    public byte[] encrypt(byte[] data, byte[] pubEncKey, AsymmCryptoType type) {
        try {
            SecureRandom random = new SecureRandom();
            byte [] nonce = new byte[16];
            random.nextBytes(nonce);
            IESParameterSpec iesParamSpec = new IESParameterSpec(null, null, 256, 256, nonce, false);

            KeyFactory kf = KeyFactory.getInstance("EC", "BC");
            PublicKey publicKey = kf.generatePublic(new X509EncodedKeySpec(pubEncKey));
            Cipher iesCipher = Cipher.getInstance("ECIESwithAES-CBC");
            iesCipher.init(Cipher.ENCRYPT_MODE, publicKey, iesParamSpec);

            byte[] res = iesCipher.doFinal(data);
            byte[] out = new byte[nonce.length + res.length];
            System.arraycopy(nonce, 0, out, 0, nonce.length);
            System.arraycopy(res, 0, out, nonce.length, res.length);
            return out;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public byte[] decrypt(byte[] data, byte[] privEncKey, AsymmCryptoType type) {
        try {
            byte[] nonce = Arrays.copyOfRange(data, 0, 16);
            byte[] ct = Arrays.copyOfRange(data, 16, data.length);
            IESParameterSpec iesParamSpec = new IESParameterSpec(null, null, 256, 256, nonce, false);

            KeyFactory kf = KeyFactory.getInstance("EC", "BC");
            PrivateKey privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(privEncKey));
            Cipher iesCipher = Cipher.getInstance("ECIESwithAES-CBC");
            iesCipher.init(Cipher.DECRYPT_MODE, privateKey, iesParamSpec);

            return iesCipher.doFinal(ct);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public byte[] sign(byte[] data, byte[] privSigKey, AsymmCryptoType type) {
        try {
            KeyFactory kf = KeyFactory.getInstance("EC", "BC");
            PrivateKey privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(privSigKey));
            Signature signature = Signature.getInstance("SHA256withECDSA");
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
            KeyFactory kf = KeyFactory.getInstance("EC", "BC");
            PublicKey publicKey = kf.generatePublic(new X509EncodedKeySpec(pubSigKey));
            Signature signature = Signature.getInstance("SHA256withECDSA");
            signature.initVerify(publicKey);
            signature.update(data);
            return signature.verify(sig);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
