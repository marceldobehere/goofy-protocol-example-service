'use client';

import {ExportIdentityKeypair, GeneralInfoDto, IrcHandleLookupDto, MyUserInfoDto} from "@/libs/dtos";
import {getAuth, getNoAuth, putAuth} from "@/libs/req";
import {getBaseServerUrl, getKeypair, hasKeypair, saveKeypair} from "@/libs/auth-store";
import {deriveHandleFromPublicSplitKey, parseFullKeypair, parsePublicSplitKey, verifyKeyAndHandle} from "@/libs/crypto";
import {goPath} from "@/libs/go-path";
import {readJsonFile, uploadData} from "@/libs/file-utils";
import {AsymmFullKeyPair} from "@/libs/crypto-types";

export async function isLoggedIn(): Promise<boolean> {
    return await hasKeypair();
}

export async function getMyHandle(): Promise<string | null> {
    if (!(await hasKeypair()))
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

export async function setUserInfo(updateDto: MyUserInfoDto) {
    if (!(await isLoggedIn()))
        return;

    await putAuth("/api/user/info", updateDto);
}

export async function logout(): Promise<void> {
    await saveKeypair(null);
    goPath("/guest/login");
}

export interface IdentityAsymmFullKeyPair extends AsymmFullKeyPair {
    handleFull: string;
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

export async function parseIdentityKeypair(kp: ExportIdentityKeypair): Promise<IdentityAsymmFullKeyPair> {
    return {
        ...parseFullKeypair({
            pub: kp.pub,
            priv: kp.priv
        }),
        handleFull: kp.handleFull
    }
}

export async function getServerDetails(serverUrl: string | null = null): Promise<GeneralInfoDto> {
    if (serverUrl == null)
        serverUrl = await getBaseServerUrl();

    return await getNoAuth(`${serverUrl}/api/general/info`);
}

// TODO: Store in session storage for x amt of time
const lookUpMap = new Map<string, IrcHandleLookupDto>();
export async function lookUpHandle(handle: string, serverUrl: string | null = null): Promise<IrcHandleLookupDto> {
    if (serverUrl == null)
        serverUrl = await getBaseServerUrl();

    const entry = lookUpMap.get(handle);
    if (entry != null)
        return entry;

    const lookup: IrcHandleLookupDto = await getNoAuth(`${serverUrl}/api/user/lookup/${handle}`);

    // Verify
    const valid = await verifyKeyAndHandle(parsePublicSplitKey(lookup.pubKey), lookup.handle);
    if (!valid) {
        alert(`Public Key & Handle mismatch for: ${handle}!`)
        throw new Error(`Public Key & Handle mismatch for: ${handle}!!!`);
    }

    lookUpMap.set(handle, lookup);
    return lookup;
}

const SUPPORTED_FIS_PROTOCOLS = ["https://", "http://"];
export async function getFisUrlsFromHandle(handle: string): Promise<string[]> {
    const lookupRes = await lookUpHandle(handle);
    if (handle.includes(":80"))
        return SUPPORTED_FIS_PROTOCOLS.toReversed().map(protocol => `${protocol}${lookupRes.handleDomain}`);
    return SUPPORTED_FIS_PROTOCOLS.map(protocol => `${protocol}${lookupRes.handleDomain}`);
}