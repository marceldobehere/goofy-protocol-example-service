'use client';

import {AsymmCryptoType as _internalAsymmCryptoType} from "@/libs/goofy-protocol-core/crypto/isolated/asymm/asymm-crypto-type.mjs";
import {SymmCryptoType as _internalSymmCryptoType} from "@/libs/goofy-protocol-core/crypto/isolated/symm/symm-crypto-type.mjs";

export class AsymmCryptoType {
    static readonly RSA_2048 = _internalAsymmCryptoType.RSA_2048 as AsymmCryptoType;
    static readonly RSA_3072 = _internalAsymmCryptoType.RSA_3072 as AsymmCryptoType;
    static readonly RSA_4096 = _internalAsymmCryptoType.RSA_4096 as AsymmCryptoType;
    static readonly EC_C25519 = _internalAsymmCryptoType.EC_C25519 as AsymmCryptoType;

    static readonly DEFAULT = AsymmCryptoType.EC_C25519;
    static readonly TYPES = [this.RSA_2048, this.RSA_3072, this.RSA_4096, this.EC_C25519];
    static fromValue(val: string): AsymmCryptoType {
        for (const type of this.TYPES)
            if (val == type.name)
                return type;
        return this.DEFAULT;
    }

    private constructor(public readonly name: string, public readonly value: number) {}
}

export class SymmCryptoType {
    static readonly AES_GCM_128 = _internalSymmCryptoType.AES_GCM_128 as SymmCryptoType;
    static readonly AES_GCM_192 = _internalSymmCryptoType.AES_GCM_192 as SymmCryptoType;
    static readonly AES_GCM_256 = _internalSymmCryptoType.AES_GCM_256 as SymmCryptoType;
    static readonly CHACHA_20 = _internalSymmCryptoType.CHACHA_20 as SymmCryptoType;

    static readonly DEFAULT = SymmCryptoType.AES_GCM_256;
    static readonly TYPES = [this.AES_GCM_128, this.AES_GCM_192, this.AES_GCM_256, this.CHACHA_20];
    static fromValue(val: string): SymmCryptoType {
        for (const type of this.TYPES)
            if (val == type.name)
                return type;
        return this.DEFAULT;
    }
    private constructor(public readonly name: string, public readonly value: number) {}
}

export interface AsymmPubKeyPair {
    sigKey: Uint8Array;
    encKey: Uint8Array;
    encSig: Uint8Array | null;
    type: AsymmCryptoType;

    serialize(): string;
}

export interface AsymmPrivKeyPair {
    sigKey: Uint8Array;
    encKey: Uint8Array;
    type: AsymmCryptoType;

    serialize(): string;
}

export interface AsymmFullKeyPair {
    pub: AsymmPubKeyPair;
    priv: AsymmPrivKeyPair;
}

// Contains Serialized Data
export interface AsymmFullJsonKeypair {
    pub: string;
    priv: string;
}

export interface SignedRequest {
    pubSplitKey: AsymmPubKeyPair;
    handle: Handle;
    signature: EncodedSignature;
    uniqueId: bigint;
    validUntil: Date;
    method: HttpMethod;
    pathHash: Uint8Array; // Uint8Array(32)
    bodyHash: Uint8Array; // Uint8Array(32)
}

export interface FullHandle {
    handle: Handle;
    optDomain: string;
}

export type HttpMethod = "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
export type Handle = string;
export type SecretString = string;
export type EncodedString = string;
export type EncodedSignature = string;