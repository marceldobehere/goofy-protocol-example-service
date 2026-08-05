'use client';

import {
    ServiceBucketEntryDto,
    ServiceEntryDto,
    ServiceTableEntryDto, ServiceTableQueryResultDto,
    TableBasicQueryDto, TableSelectDto,
    TableUpdateDto
} from "@/libs/service-dtos";
import {
    deleteFixedLockAuth,
    getFixedAuth,
    getFixedAuthBytes,
    getFixedLockAuth,
    postFixedAuth, postFixedLockAuth,
    putFixedAuth,
    putFixedLockAuth
} from "@/libs/req";
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


// Get All Tables
export async function getAllTableEntries(identityKeypair: IdentityAsymmFullKeyPair, serviceEntryUuid: string): Promise<ServiceTableEntryDto[]> {
    const identityHandle = await deriveHandleFromPublicSplitKey(identityKeypair.pub);
    return await fisReq(identityKeypair.handleFull, getFixedAuth, `/fis-api/service-table/${identityHandle}/${serviceEntryUuid}/entry`, identityKeypair);
}

// Fetch table using UUID
export async function getTableEntry(identityKeypair: IdentityAsymmFullKeyPair, serviceEntryUuid: string, tableUuid: string, lockToken: string | null = null): Promise<ServiceTableEntryDto> {
    const identityHandle = await deriveHandleFromPublicSplitKey(identityKeypair.pub);
    return await fisReq(identityKeypair.handleFull, getFixedLockAuth, `/fis-api/service-table/${identityHandle}/${serviceEntryUuid}/entry/${tableUuid}`, identityKeypair, lockToken);
}

// Create Table
export async function createTableEntry(identityKeypair: IdentityAsymmFullKeyPair, serviceEntryUuid: string, tableDto: ServiceTableEntryDto): Promise<ServiceTableEntryDto> {
    const identityHandle = await deriveHandleFromPublicSplitKey(identityKeypair.pub);
    return await fisReq(identityKeypair.handleFull, postFixedAuth, `/fis-api/service-table/${identityHandle}/${serviceEntryUuid}/entry`, tableDto, identityKeypair);
}

// Update Table (Schema + Access)
export async function updateTableEntry(identityKeypair: IdentityAsymmFullKeyPair, serviceEntryUuid: string, tableUuid: string, tableDto: ServiceTableEntryDto, lockToken: string | null = null): Promise<ServiceTableEntryDto> {
    const identityHandle = await deriveHandleFromPublicSplitKey(identityKeypair.pub);
    return await fisReq(identityKeypair.handleFull, putFixedLockAuth, `/fis-api/service-table/${identityHandle}/${serviceEntryUuid}/entry/${tableUuid}`, tableDto, identityKeypair, lockToken);
}

// Lock Table
export async function lockTableEntry(identityKeypair: IdentityAsymmFullKeyPair, serviceEntryUuid: string, tableUuid: string, readLock: boolean, writeLock: boolean, lockToken: string | null = null): Promise<string> {
    const identityHandle = await deriveHandleFromPublicSplitKey(identityKeypair.pub);
    return await fisReq(identityKeypair.handleFull, postFixedLockAuth, `/fis-api/service-table/${identityHandle}/${serviceEntryUuid}/lock/${tableUuid}?readLock=${readLock}&writeLock=${writeLock}`, "", identityKeypair, lockToken);
}

// Unlock Table
export async function unlockTableEntry(identityKeypair: IdentityAsymmFullKeyPair, serviceEntryUuid: string, tableUuid: string, readLock: boolean, writeLock: boolean, lockToken: string) {
    const identityHandle = await deriveHandleFromPublicSplitKey(identityKeypair.pub);
    await fisReq(identityKeypair.handleFull, postFixedLockAuth, `/fis-api/service-table/${identityHandle}/${serviceEntryUuid}/unlock/${tableUuid}?readLock=${readLock}&writeLock=${writeLock}`, "", identityKeypair, lockToken);
}

// Query Table
export async function queryTable(identityKeypair: IdentityAsymmFullKeyPair, serviceEntryUuid: string, tableUuid: string, selectDto: TableSelectDto, lockToken: string | null = null): Promise<ServiceTableQueryResultDto> {
    const identityHandle = await deriveHandleFromPublicSplitKey(identityKeypair.pub);
    return await fisReq(identityKeypair.handleFull, postFixedLockAuth, `/fis-api/service-table/${identityHandle}/${serviceEntryUuid}/entry/${tableUuid}/query`, selectDto, identityKeypair, lockToken);
}

// Insert Row into Table
export async function insertIntoTable(identityKeypair: IdentityAsymmFullKeyPair, serviceEntryUuid: string, tableUuid: string, row: object, lockToken: string | null = null) {
    const identityHandle = await deriveHandleFromPublicSplitKey(identityKeypair.pub);
    return await fisReq(identityKeypair.handleFull, postFixedLockAuth, `/fis-api/service-table/${identityHandle}/${serviceEntryUuid}/entry/${tableUuid}/rows`, row, identityKeypair, lockToken);
}

// Delete from Table
export async function deleteFromTable(identityKeypair: IdentityAsymmFullKeyPair, serviceEntryUuid: string, tableUuid: string, deleteQuery: TableBasicQueryDto, lockToken: string | null = null): Promise<number> {
    const identityHandle = await deriveHandleFromPublicSplitKey(identityKeypair.pub);
    return await fisReq(identityKeypair.handleFull, deleteFixedLockAuth, `/fis-api/service-table/${identityHandle}/${serviceEntryUuid}/entry/${tableUuid}/rows`, deleteQuery, identityKeypair, lockToken);
}

// Update Row(s) in Table
export async function updateTableRows(identityKeypair: IdentityAsymmFullKeyPair, serviceEntryUuid: string, tableUuid: string, updateDto: TableUpdateDto, lockToken: string | null = null): Promise<number> {
    const identityHandle = await deriveHandleFromPublicSplitKey(identityKeypair.pub);
    return await fisReq(identityKeypair.handleFull, putFixedLockAuth, `/fis-api/service-table/${identityHandle}/${serviceEntryUuid}/entry/${tableUuid}/rows`, updateDto, identityKeypair, lockToken);
}

export async function getTablePath(identityKeypair: IdentityAsymmFullKeyPair, serviceUuid: string, tableUuid: string): Promise<string> {
    const identityHandle = await deriveHandleFromPublicSplitKey(identityKeypair.pub);
    return `${identityHandle}@${serviceUuid}@${tableUuid}`;
}

// Helper Methods for Queries
