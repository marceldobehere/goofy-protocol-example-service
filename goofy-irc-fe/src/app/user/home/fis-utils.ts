'use client';

import {
    createServiceEntry,
    createTableEntry, deleteFromTable, fisReq,
    getAllTableEntries,
    getServiceEntries, getTablePath, insertIntoTable, queryTable,
    updateTableEntry, uploadBucketEntry
} from "@/libs/service-req";
import {getBaseServerUrl, getKeypair} from "@/libs/auth-store";
import {getServerDetails, getUserInfo, IdentityAsymmFullKeyPair, lookUpHandle, setUserInfo} from "@/libs/auth";
import {
    IdentityPublicData,
    LocalTableStructure, PublicGoofyIrcData, ServiceBucketEntryDto,
    ServiceEntryDto, ServicePublicDataUpdate,
    ServiceTableEntryDto,
    TableBasicQueryDto,
    TableSelectDto
} from "@/libs/service-dtos";
import {getFixedAuth, getFixedAuthBytes, putFixedAuth} from "@/libs/req";
import {deriveHandleFromPublicSplitKey} from "@/libs/crypto";
import {IrcHandleLookupDto} from "@/libs/dtos";
import {sleep} from "@/libs/utils";

const SERVICE_NAME = "DEMO Goofy IRC";
const PUBLIC_SERVICE_NAME = "Goofy IRC";

// To store the server list
const tableSchemaServerList: LocalTableStructure = {
    tableName: "current_server_list",
    schemaVersion: 4,
    handlesWithReadPerms: [],
    handlesWithWritePerms: [],
    columns: [{
        colName: "server_url",
        type: "VAR_STRING_N", typeSize: 300,
        constraints: ["UNIQUE", "NOT_NULL"]
    }, {
        colName: "server_name",
        type: "VAR_STRING_N", typeSize: 128,
        constraints: ["NOT_NULL"]
    }]
};

// To store friends and their nicknames
const tableSchemaFriendList: LocalTableStructure = {
    tableName: "friend_list",
    schemaVersion: 1,
    handlesWithReadPerms: [],
    handlesWithWritePerms: [],
    columns: [{
        colName: "handle",
        type: "VAR_STRING_N", typeSize: 128,
        constraints: ["PRIMARY_KEY", "NOT_NULL"]
    }, {
        colName: "nickname",
        type: "VAR_STRING_N", typeSize: 128,
        constraints: []
    }]
};

// To let the server send friend requests
const tableSchemaFriendRequests: LocalTableStructure = {
    tableName: "friend_requests",
    schemaVersion: 2,
    handlesWithReadPerms: [],
    handlesWithWritePerms: [],
    columns: [{
        colName: "handle",
        type: "VAR_STRING_N", typeSize: 128,
        constraints: ["PRIMARY_KEY", "NOT_NULL"]
    }, {
        colName: "requested_at",
        type: "BIGINT", // just using a number timestamp
        constraints: ["NOT_NULL"]
    }, {
        colName: "accepted",
        type: "BOOLEAN", // true -> accepted, false -> blocked, null -> not checked yet
        constraints: []
    }]
}

// Storing DMs
const tableSchemaDMs: LocalTableStructure = {
    tableName: "stored_dms",
    schemaVersion: 1,
    handlesWithReadPerms: [],
    handlesWithWritePerms: [],
    columns: [{
        colName: "uuid", // random uuid
        type: "VAR_STRING_N", typeSize: 64,
        constraints: ["PRIMARY_KEY", "NOT_NULL"]
    }, {
        colName: "msg_json", // should be encrypted, could for example store a serialized & encrypted LocalChatMessage
        type: "VAR_STRING_N", typeSize: 10_000,
        constraints: ["NOT_NULL"],
    }, {
        colName: "chat_handle", // "chat room", doesn't really need to be encrypted for this demo
        type: "VAR_STRING_N", typeSize: 128,
        constraints: ["NOT_NULL"]
    }, {
        colName: "sender_handle", // who sent the message (me or the other person), doesn't really need to be encrypted for this demo
        type: "VAR_STRING_N", typeSize: 128,
        constraints: ["NOT_NULL"]
    }, {
        colName: "timestamp", // when the message was sent, doesn't really need to be encrypted for this demo
        type: "BIGINT", // just using a number timestamp
        constraints: ["NOT_NULL"]
    }] // TODO: maybe have the user add a signature for the entire entry to avoid tampering by the FIS, idk if this is too important for this demo
}

// Received DMs
const tableSchemaReceivedDms: LocalTableStructure = {
    tableName: "received_dms",
    schemaVersion: 1,
    handlesWithReadPerms: [],
    handlesWithWritePerms: [],
    columns: [{
        colName: "uuid", // random uuid
        type: "VAR_STRING_N", typeSize: 64,
        constraints: ["PRIMARY_KEY", "NOT_NULL"]
    }, {
        colName: "msg_json", // should be encrypted, could for example store a serialized & encrypted WsReceiveMsg (or equivalent)
        type: "VAR_STRING_N", typeSize: 20_000,
        constraints: ["NOT_NULL"],
    }]
}

// TODO: Manage Concurrent Message Handling with Insert Locks

let currIdentity: IdentityAsymmFullKeyPair | null = null;
let serviceEntry: ServiceEntryDto | null = null;
let tableServerList: ServiceTableEntryDto | null = null;
let tableFriendList: ServiceTableEntryDto | null = null;
let tableFriendRequests: ServiceTableEntryDto | null = null;
let tableDms: ServiceTableEntryDto | null = null;
let tableReceivedDms: ServiceTableEntryDto | null = null;

export async function prepIfNeeded() {
    if (serviceEntry == null || currIdentity == null || tableServerList == null) {
        console.debug("For some reason the data got exploded, doing init again");
        await prepFisUtils();
    }
}

export async function prepFisUtils() {
    console.debug("FIS Utils init");

    // Prepare Service Entry
    currIdentity = await getKeypair();
    const entryPromise = prepareServiceEntry(currIdentity, SERVICE_NAME);

    // Get Server info, useful for us because we need the handle for perms
    const serverInfo = await getServerDetails();
    // console.log("Server Info: ", serverInfo);

    // Assign Perms
    tableSchemaFriendRequests.handlesWithReadPerms = [serverInfo.handle];
    tableSchemaFriendRequests.handlesWithWritePerms = [serverInfo.handle];
    tableSchemaReceivedDms.handlesWithReadPerms = [serverInfo.handle];
    tableSchemaReceivedDms.handlesWithWritePerms = [serverInfo.handle];
    tableSchemaFriendList.handlesWithReadPerms = [serverInfo.handle];
    // No write perms, just so the IRC knows which handles are friends / have DMs allowed

    // Await Promise
    serviceEntry = await entryPromise;
    console.debug("Service Entry: ", serviceEntry);

    // Setup Table for Connected Server List
    const tableServerListPromise = prepareTable(currIdentity, serviceEntry.uuid, tableSchemaServerList);
    // Setup Table for Friend List
    const tableFriendListPromise = prepareTable(currIdentity, serviceEntry.uuid, tableSchemaFriendList);
    // Setup Table for Friend Requests
    const tableFriendRequestsPromise = prepareTable(currIdentity, serviceEntry.uuid, tableSchemaFriendRequests);
    // Setup Table for DM messages (private, should be encrypted)
    const tableDmsPromise = prepareTable(currIdentity, serviceEntry.uuid, tableSchemaDMs);
    // Setup Table for new DMs (Server can write)
    const tableReceivedDmsPromise = prepareTable(currIdentity, serviceEntry.uuid, tableSchemaReceivedDms);

    // Await Promises
    tableServerList = await tableServerListPromise;
    console.debug("Table ServerList: ", tableServerList);
    tableFriendList = await tableFriendListPromise;
    console.debug("Table FriendList: ", tableFriendList);
    tableFriendRequests = await tableFriendRequestsPromise;
    console.debug("Table FriendRequests: ", tableFriendRequests);
    tableDms = await tableDmsPromise;
    console.debug("Table DMs: ", tableDms);
    tableReceivedDms = await tableReceivedDmsPromise;
    console.debug("Table Received DMs: ", tableReceivedDms);

    // Get User Info and Check Table Paths
    const userInfo = await getUserInfo(true);
    if (userInfo != null) {
        const friendRequestTablePath = await getTablePath(currIdentity, serviceEntry.uuid, tableFriendRequests.tableUuid!);
        const receivedDmsTablePath = await getTablePath(currIdentity, serviceEntry.uuid, tableReceivedDms.tableUuid!);
        const friendListTablePath = await getTablePath(currIdentity, serviceEntry.uuid, tableFriendList.tableUuid!);

        // Send IRC Server the Table Paths if needed
        if (userInfo.friendRequestTablePath != friendRequestTablePath || userInfo.receivedDmsTablePath != receivedDmsTablePath || userInfo.friendListTablePath != friendListTablePath) {
            userInfo.friendRequestTablePath = friendRequestTablePath;
            userInfo.receivedDmsTablePath = receivedDmsTablePath;
            userInfo.friendListTablePath = friendListTablePath;
            await setUserInfo(userInfo);
        }
    }

    // Link IRC Server to FIS Identity Entry if needed
    const publicFisData: IdentityPublicData = await getPublicFisData();
    if (!publicFisData.services[PUBLIC_SERVICE_NAME] && confirm(`You haven't set up your FIS Identity with the Goofy IRC Service yet. Would you like to link it?`)) {
        const newData: PublicGoofyIrcData = {
            serverUrl: await getBaseServerUrl()
        };
        await setPublicFisData(newData);
    }

    // TODO: maybe move that to diff methods
    // Lock "New DMs" Table, process messages, unlock Table
    // Update DM Status (unread messages, etc.)
    // Display Friend Requests, doesn't need processing
}

// Get/Create Service Entry with Name
async function prepareServiceEntry(identity: IdentityAsymmFullKeyPair, name: string): Promise<ServiceEntryDto> {
    const entries = await getServiceEntries(identity);

    // Find
    const maybeEntry = entries.find(e => e.name === name);
    if (maybeEntry)
        return maybeEntry;

    // Create
    await createServiceEntry(identity, name, await getBaseServerUrl());
    return prepareServiceEntry(identity, name);
}

async function prepareTable(identity: IdentityAsymmFullKeyPair, serviceUuid: string, baseTable: LocalTableStructure): Promise<ServiceTableEntryDto> {
    const entries = await getAllTableEntries(identity, serviceUuid);

    // Find
    const maybeEntry = entries.find(e => e.tableName === baseTable.tableName);
    if (maybeEntry != null) {
        // Check for Changes
        let updated = false;
        if (maybeEntry.schemaVersion! < baseTable.schemaVersion!) {
            maybeEntry.schemaVersion = baseTable.schemaVersion;
            maybeEntry.columns = baseTable.columns;
            updated = true;
        }
        if (!compStrArrays(maybeEntry.handlesWithReadPerms!, baseTable.handlesWithReadPerms!).identical) {
            maybeEntry.handlesWithReadPerms = baseTable.handlesWithReadPerms;
            updated = true;
        }
        if (!compStrArrays(maybeEntry.handlesWithWritePerms!, baseTable.handlesWithWritePerms!).identical) {
            maybeEntry.handlesWithWritePerms = baseTable.handlesWithWritePerms;
            updated = true;
        }

        // Update if needed
        if (updated)
            return await updateTableEntry(identity, serviceUuid, maybeEntry.tableUuid!, maybeEntry);
        else
            return maybeEntry;
    }

    return await createTableEntry(identity, serviceUuid, baseTable);
}

export interface StoredServerEntry {
    serverName: string;
    serverUrl: string;
}

export async function getStoredIrcServerList(): Promise<StoredServerEntry[]> {
    await prepIfNeeded();
    const query: TableSelectDto = {
        colNames: ["server_url", "server_name"]
    };

    const res = await queryTable(currIdentity!, serviceEntry!.uuid!, tableServerList!.tableUuid!, query);
    // console.log("Query Res", res);
    const list: StoredServerEntry[] = [];
    for (const row of res.rows) {
        list.push({
            serverUrl: row[0] as string,
            serverName: row[1] as string
        });
    }
    return list;
}

export async function addStoredIrcServer(entry: StoredServerEntry) {
    await prepIfNeeded();
    const insertObj = {
      "server_url": entry.serverUrl,
      "server_name": entry.serverName
    };

    await insertIntoTable(currIdentity!, serviceEntry!.uuid!, tableServerList!.tableUuid!, insertObj);
}

export async function addStoredIrcServerIfDoesntExist(entry: StoredServerEntry) {
    await prepIfNeeded();
    const vals = await getStoredIrcServerList();
    if (vals.find(v => v.serverUrl === entry.serverUrl) != null)
        return;

    await addStoredIrcServer(entry);
}

export async function deleteStoredIrcServer(serverUrl: string) {
    await prepIfNeeded();
    const deleteQuery: TableBasicQueryDto = {
        where: {
            type: "C_EQ",
            conditionParts: [
                {type: "COL", colName: "server_url"},
                {type: "VAL", value: serverUrl, valueType: "FIXED_STRING_N"}
            ]
        }
    };

    await deleteFromTable(currIdentity!, serviceEntry!.uuid!, tableServerList!.tableUuid!, deleteQuery);
}

export async function getPublicFisData(): Promise<IdentityPublicData> {
    await prepIfNeeded();
    const identityHandle = await deriveHandleFromPublicSplitKey(currIdentity!.pub);
    return await fisReq(currIdentity!.handleFull, getFixedAuth, `/fis-api/identity-storage/public/${identityHandle}`, currIdentity);
}

export async function setPublicFisData(newData: PublicGoofyIrcData) {
    await prepIfNeeded();
    const updateDto: ServicePublicDataUpdate = {
        serverName: PUBLIC_SERVICE_NAME,
        newData
    };

    // Check if data matches already
    const currData = await getPublicFisData();
    if (JSON.stringify(currData.services[PUBLIC_SERVICE_NAME]) == JSON.stringify(newData))
        return;

    // Get URL and open
    const identityHandle = await deriveHandleFromPublicSplitKey(currIdentity!.pub);
    const resUrl: string = await fisReq(currIdentity!.handleFull, putFixedAuth, `/fis-api/redirect/update-public-identity-entry/${identityHandle}`, updateDto, currIdentity);
    window.open(resUrl, "_blank")?.focus();

    // Wait for update
    const prom = new Promise((resolve) => {
        console.debug("Waiting for Public FIS Data to be updated...");
        const id = setInterval(async () => {
            const data = await getPublicFisData();
            if (JSON.stringify(data.services[PUBLIC_SERVICE_NAME]) == JSON.stringify(newData)) {
                console.debug("Public FIS Data updated successfully");
                clearInterval(id);
                resolve(null);
            }
        }, 1000);
        sleep(30_000).then(() => {
            console.debug("Timeout while waiting for Public FIS Data to be updated");
            clearInterval(id);
            resolve(null);
        })
    });
    await prom;
}

export async function uploadFisData(data: File): Promise<string | null> {
    await prepIfNeeded();
    const res = await uploadBucketEntry(currIdentity!, serviceEntry!.uuid, ["*"], data);
    const identityHandle = await deriveHandleFromPublicSplitKey(currIdentity!.pub);
    return res == null ? null : `${identityHandle}@${serviceEntry?.uuid}@${res.fileUuid}`;
}

export async function uploadPfp(reset: boolean)  {
    await prepIfNeeded();
    if (reset) {
        const data = await getPublicFisData();
        const service = data.services[PUBLIC_SERVICE_NAME];
        service.pfpPath = undefined;
        await setPublicFisData(service);
    } else {
        const res = await uploadBucketEntry(currIdentity!, serviceEntry!.uuid);
        if (res == null)
            return;

        const identityHandle = await deriveHandleFromPublicSplitKey(currIdentity!.pub);

        const data = await getPublicFisData();
        const service = data.services[PUBLIC_SERVICE_NAME];
        service.pfpPath = `${identityHandle}@${serviceEntry?.uuid}@${res.fileUuid}`;
        await setPublicFisData(service);
    }
}

export async function setDescription() {
    await prepIfNeeded();
    const desc = prompt("Enter a new description for your profile");
    if (desc == null)
        return;

    const data = await getPublicFisData();
    const service = data.services[PUBLIC_SERVICE_NAME];
    service.description = desc;
    await setPublicFisData(service);
}

export async function findPublicDataForHandle(handle: string, ircServerBase: string): Promise<PublicGoofyIrcData | null> {
    await prepIfNeeded();
    try {
        const lookup: IrcHandleLookupDto = await lookUpHandle(handle, ircServerBase);
        const data: IdentityPublicData = await fisReq(`${lookup.handle}@${lookup.handleDomain}`, getFixedAuth, `/fis-api/identity-storage/public/${handle}`, currIdentity);
        return data.services[PUBLIC_SERVICE_NAME] ?? null;
    } catch (e) {
        console.debug(`Failed to fetch info for handle ${handle} on server ${ircServerBase}:`, e);
    }
    return null;
}

export interface BucketData {
    details: ServiceBucketEntryDto;
    blob: Blob;
    blobUrl: string;
}

export async function getFisBucketData(mediaPath: string, roomServerUrl: string, maxSize: number = 10_000_000): Promise<BucketData> {
    await prepIfNeeded();
    const parts = mediaPath.split("@");

    const lookup: IrcHandleLookupDto = await lookUpHandle(parts[0], roomServerUrl);
    const fullHandle = `${lookup.handle}@${lookup.handleDomain}`;


    // Load Data
    const details: ServiceBucketEntryDto = await fisReq(fullHandle, getFixedAuth, `/fis-api/service-bucket/${lookup.handle}/${parts[1]}/entry/${parts[2]}`, currIdentity!);
    if (details.contentSize! > maxSize)
        throw new Error(`File size ${details.contentSize} exceeds max size ${maxSize}`);
    const data: Uint8Array = await fisReq(fullHandle, getFixedAuthBytes, `/fis-api/service-bucket/${lookup.handle}/${parts[1]}/content/${parts[2]}`, currIdentity!);

    // Create Blob URL
    const blob = new Blob([data as BlobPart], { type: details.contentType });
    const url = URL.createObjectURL(blob);
    return {
        details,
        blob,
        blobUrl: url
    }
}

export function compStrArrays(oldArr: string[], newArr: string[]): { identical: boolean; addedValues: string[]; removedValues: string[] } {
    const oldSet = new Set(oldArr);
    const newSet = new Set(newArr);

    const addedValues: string[] = [];
    for (const v of newSet)
        if (!oldSet.has(v))
            addedValues.push(v);

    const removedValues: string[] = [];
    for (const v of oldSet)
        if (!newSet.has(v))
            removedValues.push(v);


    const identical = addedValues.length === 0 && removedValues.length === 0;
    return { identical, addedValues, removedValues };
}