'use client';

import {getNoAuth, postFixedAuth, postFixedAuthDomain} from "@/libs/req";
import {RegistrationRequestDto, RequestError, RequestIrcError} from "@/libs/dtos";
import {IdentityAsymmFullKeyPair} from "@/libs/auth";

export async function isRegisterCodeValid(code: string): Promise<boolean> {
    return await getNoAuth<boolean>("/api/register/valid?code=" + code);
}

export async function sendRegistrationRequest(request: RegistrationRequestDto, keypair: IdentityAsymmFullKeyPair): Promise<string | null> {
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

export async function doRegistration(code: string, keypair: IdentityAsymmFullKeyPair, domain: string): Promise<string | null> {
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