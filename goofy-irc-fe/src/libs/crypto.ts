'use client';

import {
    AsymmCryptoType,
    AsymmFullJsonKeypair,
    AsymmFullKeyPair,
    AsymmPrivKeyPair,
    AsymmPubKeyPair,
    EncodedSignature,
    EncodedString,
    FullHandle,
    Handle,
    HttpMethod,
    SecretString,
    SignedRequest,
    SymmCryptoType
} from "@/libs/crypto-types";

import {GlobAsymmCrypto} from "@/libs/goofy-protocol-core/crypto/isolated/asymm/glob-asymm-crypto.mjs";
import {GlobSymmCrypto} from "@/libs/goofy-protocol-core/crypto/isolated/symm/glob-symm-crypto.mjs";
import {
    AsymmPrivKeyPair as _internalAsymmPrivKeyPair,
    AsymmPubKeyPair as _internalAsymmPubKeyPair
} from "@/libs/goofy-protocol-core/crypto/isolated/asymm/asymm-crypto-interface.mjs";
import {
    SignedRequest as _internalSignedRequest
} from "@/libs/goofy-protocol-core/crypto/connected/request/signed-request.mjs"
import {HandleCrypto} from "@/libs/goofy-protocol-core/crypto/connected/handle-crypto.mjs";
import {SampleHandleHelper} from "@/libs/goofy-protocol-core/crypto/connected/sample-handle-helper.mjs";
import {sha256} from "@/libs/goofy-protocol-core/crypto/isolated/secret-utils.mjs";

const asymmCrypto = new GlobAsymmCrypto();
const symmCrypto = new GlobSymmCrypto();
const handleCryptoHelper = new SampleHandleHelper();
const handleCrypto = new HandleCrypto(handleCryptoHelper);

// Asymmetric Stuff

export async function generateAsymmKeypair(type: AsymmCryptoType = AsymmCryptoType.DEFAULT): Promise<AsymmFullKeyPair> {
    return await asymmCrypto.generateKeypair(type);
}

export async function checkPublicSplitKey(pubSplitKey: AsymmPubKeyPair) {
    return await asymmCrypto.checkPublicSplitKey(pubSplitKey.serialize());
}

export function parsePublicSplitKey(str: string): AsymmPubKeyPair {
    return _internalAsymmPubKeyPair.parse(str) as AsymmPubKeyPair;
}

export function parsePrivateSplitKey(str: string): AsymmPrivKeyPair {
    return _internalAsymmPrivKeyPair.parse(str) as AsymmPrivKeyPair;
}

export function serializeFullKeypair(keypair: AsymmFullKeyPair): AsymmFullJsonKeypair {
    return {
        pub: keypair.pub.serialize(),
        priv: keypair.priv.serialize(),
    }
}

export function parseFullKeypair(keypair: AsymmFullJsonKeypair): AsymmFullKeyPair {
    return {
        pub: parsePublicSplitKey(keypair.pub),
        priv: parsePrivateSplitKey(keypair.priv),
    };
}

export async function asymmEncryptStr(str: string, pubSplitKey: AsymmPubKeyPair): Promise<EncodedString> {
    return await asymmCrypto.encryptStr(str, pubSplitKey.serialize());
}

export async function asymmDecryptStr(encryptedStr: EncodedString, privSplitKey: AsymmPrivKeyPair): Promise<string> {
    return await asymmCrypto.decryptStr(encryptedStr, privSplitKey.serialize());
}

export async function asymmEncryptObj<T>(obj: T, pubSplitKey: AsymmPubKeyPair): Promise<EncodedString> {
    return await asymmCrypto.encryptStr(JSON.stringify(obj), pubSplitKey.serialize());
}

export async function asymmDecryptObj<T>(encryptedStr: EncodedString, privSplitKey: AsymmPrivKeyPair): Promise<T> {
    return JSON.parse(await asymmCrypto.decryptStr(encryptedStr, privSplitKey.serialize())) as T;
}

export async function asymmSignStr(str: string, privSplitKey: AsymmPrivKeyPair): Promise<EncodedSignature> {
    return await asymmCrypto.signStr(str, privSplitKey.serialize());
}

export async function asymmVerifyStr(str: string, signature: EncodedSignature, pubSplitKey: AsymmPubKeyPair): Promise<boolean> {
    return await asymmCrypto.verifyStr(str, signature, pubSplitKey.serialize());
}

export async function asymmSignObj<T>(obj: T, privSplitKey: AsymmPrivKeyPair): Promise<EncodedSignature> {
    return await asymmCrypto.signStr(JSON.stringify(obj), privSplitKey.serialize());
}

export async function asymmVerifyObj<T>(obj: T, signature: EncodedSignature, pubSplitKey: AsymmPubKeyPair): Promise<boolean> {
    return await asymmCrypto.verifyStr(JSON.stringify(obj), signature, pubSplitKey.serialize());
}

// Symmetric Stuff

export async function symmEncryptStr(str: string, secret: SecretString, type: SymmCryptoType = SymmCryptoType.DEFAULT): Promise<EncodedString> {
    return await symmCrypto.encryptStr(str, secret, type);
}

export async function symmDecryptStr(str: string, secret: SecretString): Promise<string> {
    return await symmCrypto.decryptStr(str, secret) as string;
}

export async function symmEncryptObj<T>(obj: T, secret: SecretString, type: SymmCryptoType = SymmCryptoType.DEFAULT): Promise<EncodedString> {
    return await symmCrypto.encryptStr(JSON.stringify(obj), secret, type);
}

export async function symmDecryptObj<T>(str: string, secret: SecretString): Promise<T> {
    return JSON.parse(await symmCrypto.decryptStr(str, secret) as string) as T;
}

// Handle Stuff

// TODO: Cache
export async function deriveHandleFromPublicSplitKey(pubSplitKey: AsymmPubKeyPair): Promise<Handle> {
    return await handleCrypto.deriveHandle(pubSplitKey.serialize());
}

export async function verifyKeyAndHandle(pubSplitKey: AsymmPubKeyPair, handle: Handle): Promise<boolean> {
    return await handleCrypto.verifyKeyAndHandle(pubSplitKey.serialize(), handle);
}

// TODO: Implement external lookup
export async function getPublicSplitKeyFromHandle(handle: Handle): Promise<AsymmPubKeyPair | null> {
    const pubSplitKeyStr = await handleCrypto.getPublicSplitKeyFromHandle(handle) as string | null;
    if (!pubSplitKeyStr)
        return null;
    return parsePublicSplitKey(pubSplitKeyStr);
}

export function parseFullHandle(handleWithOptDomain: string): FullHandle {
    return {
        handle: SampleHandleHelper.stripPotentialDomainFromHandle(handleWithOptDomain),
        optDomain: SampleHandleHelper.getPotentialDomainFromHandle(handleWithOptDomain) || "",
    }
}

// Signed Request

export async function createSignedRequest(keypair: AsymmFullKeyPair, method: HttpMethod, path: string, body: string | Uint8Array | null): Promise<SignedRequest> {
    return await _internalSignedRequest.fromParts(keypair, method, path, body, handleCrypto) as SignedRequest;
}

export function getHeadersFromSignedRequestWithPubkey(signedRequest: SignedRequest): Map<string, string> {
    return (signedRequest as _internalSignedRequest).toHeadersWithPubKey();
}

export function getHeadersFromSignedRequestWithHandle(signedRequest: SignedRequest, optHandleDomain: string | null = null): Map<string, string> {
    return (signedRequest as _internalSignedRequest).toHeadersWithHandle(optHandleDomain as never);
}

// Hash
export function base64Encode(bytes: Uint8Array): string {
    let binary = "";
    const chunkSize = 0x8000;
    for (let i = 0; i < bytes.length; i += chunkSize) {
        binary += String.fromCharCode(...bytes.subarray(i, i + chunkSize));
    }
    return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_");
}

export async function sha256ToText(inputStr: string): Promise<string> {
    const hash = await sha256(inputStr);
    return base64Encode(hash);
}

export async function secretSymmKeyFromPrivateKey(privKey: AsymmPrivKeyPair): Promise<string> {
    return await sha256ToText(privKey.serialize());
}

export async function secretSymmKeyFromFullKey(keypair: AsymmFullKeyPair): Promise<string> {
    return await sha256ToText(keypair.priv.serialize());
}