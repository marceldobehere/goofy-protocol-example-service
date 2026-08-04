'use client';

import {AsymmFullJsonKeypair, AsymmFullKeyPair} from "@/libs/crypto-types";
import {getNoAuth, postFixedAuth, postFixedAuthDomain} from "@/libs/req";
import {RegistrationRequestDto, RequestError, RequestIrcError} from "@/libs/dtos";
import {parseFullKeypair, sha256ToText, symmDecryptObj} from "@/libs/crypto";

export async function isRegisterCodeValid(code: string): Promise<boolean> {
    return await getNoAuth<boolean>("/api/register/valid?code=" + code);
}

export async function sendRegistrationRequest(request: RegistrationRequestDto, keypair: AsymmFullKeyPair): Promise<string | null> {
    try {
        await postFixedAuth("/api/register/request", request, keypair);
        return null;
    } catch (e) {
        if (e instanceof RequestError)
                return e.message;
        else if (e instanceof RequestIrcError)
            return e.message + ` (Details: ${JSON.stringify(e.details)})`;
        return (e as Error).message;
    }
}

export async function doRegistration(code: string, keypair: AsymmFullKeyPair, domain: string): Promise<string | null> {
    try {
        await postFixedAuthDomain("/api/register", code, keypair, domain);
        return null;
    } catch (e) {
        if (e instanceof RequestError)
            return e.message;
        else if (e instanceof RequestIrcError)
            return e.message;
        return (e as Error).message;
    }
}

export async function loadLogin(username: string, password: string): Promise<AsymmFullKeyPair | null> {
    try {
        const usernameHash = await sha256ToText(username);
        const pwHash = await sha256ToText(password);

        const encKeypair = await getNoAuth<string>("/api/login-storage/" + encodeURIComponent(usernameHash));
        const jsonKeypair = await symmDecryptObj<AsymmFullJsonKeypair>(encKeypair, pwHash);
        return parseFullKeypair(jsonKeypair);
    } catch {
        return null;
    }
}