package com.masl.goofy_protocol_core.crypto.isolated.asymm;

import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;

import static com.masl.goofy_protocol_core.crypto.isolated.SecretUtils.ENC_DELIMITER;

public interface AsymmCrypto {
    List<AsymmCryptoType> getTypes();
    boolean checkPubKeyPair(AsymmPubKeyPair pubKeyPair, AsymmCryptoType type);
    AsymmFullKeyPair generateKeypair(AsymmCryptoType type);

    byte[] encrypt(byte[] data, byte[] pubEncKey, AsymmCryptoType type);
    byte[] decrypt(byte[] data, byte[] privEncKey, AsymmCryptoType type);

    byte[] sign(byte[] data, byte[] privSigKey, AsymmCryptoType type);
    boolean verify(byte[] data, byte[] sig, byte[] pubSigKey, AsymmCryptoType type);

    // PUB.[TYPE].[SIG KEY].[ENC KEY].[ENC SIG]
    record AsymmPubKeyPair(byte[] sigKey, byte[] encKey, byte[] encSig, AsymmCryptoType type) {
        public static AsymmPubKeyPair parse(String value) {
            String[] parts = value.split(Pattern.quote(ENC_DELIMITER));
            if (parts.length != 5)
                throw new IllegalArgumentException("Invalid split key format");
            if (!parts[0].equals("PUB"))
                throw new IllegalArgumentException("Invalid split key type");
            AsymmCryptoType type = AsymmCryptoType.valueOf(parts[1]);
            byte[] sigKey = Base64.getUrlDecoder().decode(parts[2]);
            if (parts[3].equals(parts[4]) && parts[3].equals("X")) {
                return new AsymmPubKeyPair(sigKey, sigKey, null, type);
            } else {
                byte[] encKey = Base64.getUrlDecoder().decode(parts[3]);
                byte[] encSig = Base64.getUrlDecoder().decode(parts[4]);
                return new AsymmPubKeyPair(sigKey, encKey, encSig, type);
            }
        }

        public String serialize() {
            if (Arrays.equals(sigKey, encKey))
                return "PUB" + ENC_DELIMITER + type.toString() +
                        ENC_DELIMITER + Base64.getUrlEncoder().encodeToString(sigKey) +
                        ENC_DELIMITER + "X" +
                        ENC_DELIMITER + "X";
            return "PUB" + ENC_DELIMITER + type.toString() +
                    ENC_DELIMITER + Base64.getUrlEncoder().encodeToString(sigKey) +
                    ENC_DELIMITER + Base64.getUrlEncoder().encodeToString(encKey) +
                    ENC_DELIMITER + Base64.getUrlEncoder().encodeToString(encSig);
        }

        @SuppressWarnings("BooleanMethodIsAlwaysInverted")
        public boolean isSigValid(AsymmCrypto crypto) {
            if (Arrays.equals(sigKey, encKey))
                return true;
            return crypto.verify(encKey, encSig, sigKey, type);
        }
    }

    // PRIV.[TYPE].[SIG KEY].[ENC KEY]
    record AsymmPrivKeyPair(byte[] sigKey, byte[] encKey, AsymmCryptoType type) {
        public static AsymmPrivKeyPair parse(String value) {
            String[] parts = value.split(Pattern.quote(ENC_DELIMITER));
            if (parts.length != 4)
                throw new IllegalArgumentException("Invalid split key format");
            if (!parts[0].equals("PRIV"))
                throw new IllegalArgumentException("Invalid split key type");
            AsymmCryptoType type = AsymmCryptoType.valueOf(parts[1]);
            byte[] sigKey = Base64.getUrlDecoder().decode(parts[2]);
            if (parts[3].equals("X")) {
                return new AsymmPrivKeyPair(sigKey, sigKey, type);
            } else {
                byte[] encKey = Base64.getUrlDecoder().decode(parts[3]);
                return new AsymmPrivKeyPair(sigKey, encKey, type);
            }
        }

        public String serialize() {
            if (Arrays.equals(sigKey, encKey))
                return "PRIV" + ENC_DELIMITER + type.toString() +
                        ENC_DELIMITER + Base64.getUrlEncoder().encodeToString(sigKey) +
                        ENC_DELIMITER + "X";

            return "PRIV" + ENC_DELIMITER + type.toString() +
                    ENC_DELIMITER + Base64.getUrlEncoder().encodeToString(sigKey) +
                    ENC_DELIMITER + Base64.getUrlEncoder().encodeToString(encKey);
        }
    }

    record AsymmFullKeyPair(AsymmPubKeyPair pub, AsymmPrivKeyPair priv) {
        public static AsymmFullKeyPair fromParts(byte[] pubSigKey, byte[] pubEncKey, byte[] privSigKey, byte[] privEncKey, AsymmCrypto crypto, AsymmCryptoType type) {
            AsymmPrivKeyPair priv = new AsymmPrivKeyPair(privSigKey, privEncKey, type);
            byte[] encSig = Arrays.equals(pubEncKey, pubSigKey) ? null : crypto.sign(pubEncKey, privSigKey, type);
            AsymmPubKeyPair pub = new AsymmPubKeyPair(pubSigKey, pubEncKey, encSig, type);
            return new AsymmFullKeyPair(pub, priv);
        }

        public static AsymmFullKeyPair fromParts(String pubSplitKey, String privSplitKey) {
            return new AsymmFullKeyPair(AsymmPubKeyPair.parse(pubSplitKey), AsymmPrivKeyPair.parse(privSplitKey));
        }
    }
}

