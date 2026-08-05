'use client';

import {HttpMethod} from "@/libs/crypto-types";
import {createSignedRequest, getHeadersFromSignedRequestWithHandle, parseFullHandle} from "@/libs/crypto";
import {AllServerErrorCodes, IrcExceptionDto, RequestError, RequestIrcError} from "@/libs/dtos";
import {getBaseServerUrl, getKeypair} from "@/libs/auth-store";
import {SpinActivity} from "@/libs/spinner";
import {IdentityAsymmFullKeyPair} from "@/libs/auth";

function _isBinaryBody(body: object): body is Uint8Array {
    return body instanceof Uint8Array;
}

let overrideDomain: string | null = null;

export async function _internalDoReq<T>(_path: string, method: HttpMethod, body: object | Uint8Array | string | null, keypair: IdentityAsymmFullKeyPair | null = null, extraHeaders: Map<string, string> = new Map(), bodyBytes: boolean = false, rawResponse: boolean = false, sendHandle: boolean = true): Promise<T | Response> {
    const headers: Map<string, string> = new Map(extraHeaders);
    const isBodyStr = body != null && typeof body === "string";
    const isBodyBinary = body != null && _isBinaryBody(body as object);
    if (!isBodyStr && body != null && !isBodyBinary)
        headers.set("Content-Type", "application/json");

    // Fix Path
    let path = _path;
    if (path.startsWith("/"))
        path = await getBaseServerUrl() + path;
    const basePath = new URL(path).pathname;

    // Generate Signed Request and add Headers
    if (keypair != null) {
        const bodyVal = (body == null) ? null : ((isBodyStr || isBodyBinary) ? body : JSON.stringify(body));
        const req = await createSignedRequest(keypair, method, basePath, bodyVal as Uint8Array | string | null);
        const parsedFullHandle = parseFullHandle(keypair.handleFull);
        const reqHeaders = sendHandle ? getHeadersFromSignedRequestWithHandle(req, overrideDomain) : getHeadersFromSignedRequestWithHandle(req, parsedFullHandle.optDomain); // getHeadersFromSignedRequestWithPubkey(req);
        for (const [key, value] of reqHeaders.entries())
            headers.set(key, value);
    }

    // Prepare Request Options
    const reqOptions: RequestInit = {
        method,
        headers: headers.entries().toArray(),
    };

    // Add Body if needed
    if (body != null)
        reqOptions.body = (isBodyStr || isBodyBinary) ? body as BodyInit : JSON.stringify(body);

    // console.log(`> Sending ${method} Request to ${path} ${keypair == null ? 'without auth' : 'with auth'} and options: `, reqOptions, body);

    // Execute fetch
    let res = await fetch(path, reqOptions);
    // console.log(`< Received Response from ${path} with status ${res.status} and ok=${res.ok}`, res);

    // Check for PublicKeyLookupFailed Error (if sendHandle is enabled) and retry if needed
    if (keypair != null && sendHandle && !res.ok) {
        const resBodyStr = await res.text();
        try {
            const resBody = JSON.parse(resBodyStr);
            if (resBody satisfies IrcExceptionDto && (resBody as IrcExceptionDto).errorCode == AllServerErrorCodes.PUBLIC_KEY_LOOKUP_FAILED ) {
                return await doRequestSpinner<T>(_path, method, body, keypair, extraHeaders, bodyBytes, rawResponse, false);
            }
        } catch (e) {
            if (e instanceof RequestError || e instanceof RequestIrcError)
                throw e;
        }

        // Reconstruct Response and hope its fine
        res = new Response(resBodyStr, {status: res.status, statusText: res.statusText, headers: res.headers});
    }

    // Send raw Response
    if (rawResponse)
        return res;

    // If Response is not ok, throw Error in shape of RequestIrcError (Irc Exceptions) or RequestError (General Exceptions)
    if (!res.ok) {
        const errorStr = await res.text();
        let errorObj: IrcExceptionDto;
        try {
            errorObj = JSON.parse(errorStr) as IrcExceptionDto;
        } catch {
            throw new RequestError(res.status, errorStr);
        }

        if (errorObj.errorCode != null)
            throw new RequestIrcError(res.status, errorObj);
        else
            throw new RequestError(res.status, errorStr);
    }

    // Get Raw Bytes if wanted
    if (bodyBytes)
        return await res.bytes() as T;

    // Convert to String or Object
    const resStr = await res.text();
    try {
        return JSON.parse(resStr) as T;
    } catch {
        return resStr as T;
    }
}

export async function doRequestSpinner<T>(_path: string, method: HttpMethod, body: object | Uint8Array | string | null, keypair: IdentityAsymmFullKeyPair | null = null, extraHeaders: Map<string, string> = new Map(), bodyBytes: boolean = false, rawResponse: boolean = false, sendHandle: boolean = true): Promise<T | Response> {
    let res;
    await SpinActivity(async () => {
        res = await _internalDoReq<T>(_path, method, body, keypair, extraHeaders, bodyBytes, rawResponse, sendHandle);
    });
    return res as T | Response;
}

export async function getNoAuth<T>(path: string): Promise<T> {
    return await doRequestSpinner<T>(path, "GET", null) as T;
}
export async function getRawNoAuth(path: string): Promise<Response> {
    return await doRequestSpinner<Response>(path, "GET", null, null, new Map(), false, true) as Response;
}
export async function getAuth<T>(path: string): Promise<T> {
    return await doRequestSpinner<T>(path, "GET", null, await getKeypair()) as T;
}
export async function getFixedAuth<T>(path: string, keypair: IdentityAsymmFullKeyPair): Promise<T> {
    return await doRequestSpinner<T>(path, "GET", null, keypair) as T;
}
export async function getFixedAuthBytes<T>(path: string, keypair: IdentityAsymmFullKeyPair): Promise<T> {
    return await doRequestSpinner<T>(path, "GET", null, keypair, new Map(), true) as T;
}

export async function postNoAuth<T>(path: string, body: object | string ) {
    return await doRequestSpinner<T>(path, "POST", body) as T;
}
export async function postRawNoAuth(path: string, body: object | string ): Promise<Response> {
    return await doRequestSpinner<Response>(path, "POST", body, null, new Map(), false, true) as Response;
}
export async function postAuth<T>(path: string, body: object | string ) {
    return await doRequestSpinner<T>(path, "POST", body, await getKeypair()) as T;
}
export async function postFixedAuth<T>(path: string, body: object | string , keypair: IdentityAsymmFullKeyPair, extraHeaders: Map<string, string> = new Map()) {
    return await doRequestSpinner<T>(path, "POST", body, keypair, extraHeaders) as T;
}
export async function postFixedAuthDomain<T>(path: string, body: object | string , keypair: IdentityAsymmFullKeyPair, extraDomain: string, extraHeaders: Map<string, string> = new Map()) {
    overrideDomain = extraDomain;
    try {
        const res = await doRequestSpinner<T>(path, "POST", body, keypair, extraHeaders) as T;
        overrideDomain = null;
        return res;
    } catch (e) {
        overrideDomain = null;
        throw e;
    }
}

export async function deleteNoAuth<T>(path: string): Promise<T> {
    return await doRequestSpinner<T>(path, "DELETE", null) as T;
}
export async function deleteRawNoAuth(path: string): Promise<Response> {
    return await doRequestSpinner<Response>(path, "DELETE", null, null, new Map(), false, true) as Response;
}
export async function deleteAuth<T>(path: string): Promise<T> {
    return await doRequestSpinner<T>(path, "DELETE", null, await getKeypair()) as T;
}
export async function deleteFixedAuth<T>(path: string, keypair: IdentityAsymmFullKeyPair): Promise<T> {
    return await doRequestSpinner<T>(path, "DELETE", null, keypair) as T;
}
export async function deleteBodyFixedAuth<T>(path: string, body: object | string, keypair: IdentityAsymmFullKeyPair): Promise<T> {
    return await doRequestSpinner<T>(path, "DELETE", body, keypair) as T;
}

export async function putNoAuth<T>(path: string, body: object | string ) {
    return await doRequestSpinner<T>(path, "PUT", body) as T;
}
export async function putRawNoAuth(path: string, body: object | string ): Promise<Response> {
    return await doRequestSpinner<Response>(path, "PUT", body, null, new Map(), false, true) as Response;
}
export async function putAuth<T>(path: string, body: object | string ) {
    return await doRequestSpinner<T>(path, "PUT", body, await getKeypair()) as T;
}
export async function putFixedAuth<T>(path: string, body: object | string , keypair: IdentityAsymmFullKeyPair) {
    return await doRequestSpinner<T>(path, "PUT", body, keypair) as T;
}

export async function getFixedLockAuth<T>(path: string, keypair: IdentityAsymmFullKeyPair, lockToken: string | null): Promise<T> {
    const extraHeaders: Map<string, string> = new Map();
    if (lockToken != null)
        extraHeaders.set("X-Lock-Token", lockToken);
    return await doRequestSpinner<T>(path, "GET", null, keypair, extraHeaders) as T;
}
export async function postFixedLockAuth<T>(path: string, body: object | string , keypair: IdentityAsymmFullKeyPair, lockToken: string | null, extraHeaders: Map<string, string> = new Map()) {
    if (lockToken != null)
        extraHeaders.set("X-Lock-Token", lockToken);
    return await doRequestSpinner<T>(path, "POST", body, keypair, extraHeaders) as T;
}
export async function putFixedLockAuth<T>(path: string, body: object | string , keypair: IdentityAsymmFullKeyPair, lockToken: string | null) {
    const extraHeaders: Map<string, string> = new Map();
    if (lockToken != null)
        extraHeaders.set("X-Lock-Token", lockToken);
    return await doRequestSpinner<T>(path, "PUT", body, keypair, extraHeaders) as T;
}
export async function deleteFixedLockAuth<T>(path: string, keypair: IdentityAsymmFullKeyPair, lockToken: string | null): Promise<T> {
    const extraHeaders: Map<string, string> = new Map();
    if (lockToken != null)
        extraHeaders.set("X-Lock-Token", lockToken);
    return await doRequestSpinner<T>(path, "DELETE", null, keypair, extraHeaders) as T;
}