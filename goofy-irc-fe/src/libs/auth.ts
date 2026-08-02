'use client';

import {ExportIdentityKeypair, MyUserInfoDto} from "@/libs/dtos";
import {getAuth} from "@/libs/req";
import {getKeypair, hasKeypair, saveKeypair} from "@/libs/auth-store";
import {deriveHandleFromPublicSplitKey, parseFullKeypair} from "@/libs/crypto";
import {goPath} from "@/libs/go-path";
import {readJsonFile, uploadData} from "@/libs/file-utils";
import {AsymmFullKeyPair} from "@/libs/crypto-types";

export async function isLoggedIn(): Promise<boolean> {
    return await hasKeypair();
}

export async function getMyHandle(): Promise<string | null> {
    if (!hasKeypair())
        return null;

    const keypair = await getKeypair();
    return await deriveHandleFromPublicSplitKey(keypair.pub);
}

export async function isUser(): Promise<boolean> {
    if (!(await isLoggedIn()))
        return false;

    try {
        const res: MyUserInfoDto = await getAuth("/api/user/info");
        return res.authRole == "REGISTERED_USER" || res.authRole == "ADMIN";
    } catch {
        return false;
    }
}

export async function isAdmin(): Promise<boolean> {
    if (!(await isLoggedIn()))
        return false;

    try {
        const res: MyUserInfoDto = await getAuth("/api/user/info");
        return res.authRole == "ADMIN";
    } catch {
        return false;
    }
}

export async function getUserInfo(throwError: boolean = false): Promise<MyUserInfoDto | null> {
    if (!(await isLoggedIn()))
        return null;

    try {
        const res: MyUserInfoDto = await getAuth("/api/user/info");
        const derivedHandle = await getMyHandle();
        if (res.handle != derivedHandle) {
            alert(`Derived handle ${derivedHandle} does not match server handle ${res.handle}`);
            return null;
        }
        return res;
    } catch (e) {
        if (throwError)
            throw e;
        return null;
    }
}

export async function logout(): Promise<void> {
    await saveKeypair(null);
    goPath("/guest/login");
}



export async function importIdentityKeypair(): Promise<ExportIdentityKeypair | null> {
    const importKeypairFile: File | null = await uploadData(false) as File;
    if (importKeypairFile == null)
        return null;

    const importKeypairObj = await readJsonFile<ExportIdentityKeypair>(importKeypairFile);
    if (importKeypairObj == null || importKeypairObj.pub == null || importKeypairObj.priv == null || importKeypairObj.handleFull == null)
        return null;
    return importKeypairObj;
}

export async function parseIdentityKeypair(kp: ExportIdentityKeypair): Promise<AsymmFullKeyPair> {
    return parseFullKeypair({
        pub: kp.pub,
        priv: kp.priv
    })
}