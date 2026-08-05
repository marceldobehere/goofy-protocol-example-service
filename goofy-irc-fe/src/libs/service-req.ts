'use client';

import {ServiceBucketEntryDto, ServiceEntryDto} from "@/libs/service-dtos";
import {getFixedAuth, getFixedAuthBytes, postFixedAuth, putFixedAuth} from "@/libs/req";
import {getFisUrlsFromHandle, IdentityAsymmFullKeyPair} from "@/libs/auth";
import {readFileBytes, uploadData} from "@/libs/file-utils";
import {deriveHandleFromPublicSplitKey, parseFullHandle} from "@/libs/crypto";
import {isNetworkErrorTypeError} from "@/libs/global-state";

// eslint-disable-next-line @typescript-eslint/no-unsafe-function-type
export async function fisReq(fullHandle: string, func: Function, path: string, ...args: unknown[]) {
    const baseUrls = await getFisUrlsFromHandle(fullHandle);

    let lastErr: Error | null = null;
    for (const baseUrl of baseUrls)
        try {
            return await func(baseUrl + path, ...args);
        } catch (e) {
            if (lastErr == null || !isNetworkErrorTypeError(e))
                lastErr = e as Error;
            console.warn(`Error calling ${baseUrl + path}: ${e}`);
        }

    throw lastErr;
}

export async function getServiceEntries(identityKeypair: IdentityAsymmFullKeyPair): Promise<ServiceEntryDto[]> {
    return await fisReq(identityKeypair.handleFull, getFixedAuth, "/fis-api/service-entry", identityKeypair);
}

export async function getServiceEntry(identityKeypair: IdentityAsymmFullKeyPair, uuid: string): Promise<ServiceEntryDto> {
    return await fisReq(identityKeypair.handleFull, getFixedAuth, "/fis-api/service-entry/" + encodeURIComponent(uuid), identityKeypair);
}

export async function createServiceEntry(identityKeypair: IdentityAsymmFullKeyPair, name: string, usedServiceName: string) {
    const newEntry: ServiceEntryDto = {
        name: name,
        usedService: usedServiceName,
        uuid: "" // Keep blank
    }

    await fisReq(identityKeypair.handleFull, postFixedAuth, "/fis-api/service-entry", newEntry, identityKeypair);
}

export async function uploadBucketEntry(identityKeypair: IdentityAsymmFullKeyPair, serviceEntryUuid: string, readAccess: string[] = ["*"]): Promise<ServiceBucketEntryDto | null> {
    const data: File | null = await uploadData(false) as File;
    if (data == null)
        return null;

    // Read File
    const filename = data.name;
    const dataType = data.type;
    const bytes = await readFileBytes(data);

    // Upload
    const identityHandle = await deriveHandleFromPublicSplitKey(identityKeypair.pub);
    const baseBucketUrl = `/fis-api/service-bucket/${identityHandle}/${serviceEntryUuid}`;
    const detailsDto: ServiceBucketEntryDto = await fisReq(identityHandle, postFixedAuth, `${baseBucketUrl}/upload`, bytes, identityKeypair, new Map([["Content-Type", dataType], ["X-Filename", encodeURIComponent(filename)]]));

    // Set Perms
    detailsDto.handlesWithReadPerms = readAccess;
    await fisReq(identityHandle, putFixedAuth, `${baseBucketUrl}/entry/${detailsDto.fileUuid}`, detailsDto, identityKeypair);
    return detailsDto;
}

export async function fetchBucketEntry(identityKeypair: IdentityAsymmFullKeyPair, fullHandle: string, serviceUuid: string, fileUuid: string) {
    const parsed = parseFullHandle(fullHandle);

    // Load Data
    const details: ServiceBucketEntryDto = await fisReq(fullHandle, getFixedAuth, `/fis-api/service-bucket/${parsed.handle}/${serviceUuid}/entry/${fileUuid}`, identityKeypair);
    const data: Uint8Array = await fisReq(fullHandle, getFixedAuthBytes, `/fis-api/service-bucket/${parsed.handle}/${serviceUuid}/content/${fileUuid}`, identityKeypair);

    // Create Blob URL
    const blob = new Blob([data as BlobPart], { type: details.contentType });
    const url = URL.createObjectURL(blob);

    // Open Window
    window.open(url, "_blank");

    // Cleanup
    setTimeout(() => URL.revokeObjectURL(url), 60_000);
}