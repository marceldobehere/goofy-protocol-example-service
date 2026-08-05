'use client';

import {
    createServiceEntry,
    createTableEntry,
    getAllTableEntries,
    getServiceEntries, getTablePath,
    updateTableEntry
} from "@/libs/service-req";
import {getBaseServerUrl, getKeypair} from "@/libs/auth-store";
import {getServerDetails, getUserInfo, IdentityAsymmFullKeyPair, setUserInfo} from "@/libs/auth";
import {LocalTableStructure, ServiceEntryDto, ServiceTableEntryDto} from "@/libs/service-dtos";

const SERVICE_NAME = "DEMO Goofy IRC";

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

// TODO: Implement or remove
// // Test Upload
// console.log("Test Upload");
// const res = await uploadBucketEntry(identity, entry.uuid);
// console.log("Bucket Entry: ", res);

// TODO: Manage Concurrent Message Handling with Insert Locks
export async function prepFisUtils() {
    console.log("FIS Utils init");

    // Prepare Service Entry
    const identity = await getKeypair();
    const entryPromise = prepareServiceEntry(identity, SERVICE_NAME);

    // Get Server info, useful for us because we need the handle for perms
    const serverInfo = await getServerDetails();
    console.log("Server Info: ", serverInfo);

    // Assign Perms
    tableSchemaFriendRequests.handlesWithReadPerms = [serverInfo.handle];
    tableSchemaFriendRequests.handlesWithWritePerms = [serverInfo.handle];
    tableSchemaReceivedDms.handlesWithWritePerms = [serverInfo.handle];
    tableSchemaReceivedDms.handlesWithWritePerms = [serverInfo.handle];

    // Await Promise
    const entry = await entryPromise;
    console.log("Service Entry: ", entry);

    // Setup Table for Connected Server List
    const tableServerListPromise = prepareTable(identity, entry.uuid, tableSchemaServerList);
    // Setup Table for Friend List
    const tableFriendListPromise = prepareTable(identity, entry.uuid, tableSchemaFriendList);
    // Setup Table for Friend Requests
    const tableFriendRequestsPromise = prepareTable(identity, entry.uuid, tableSchemaFriendRequests);
    // Setup Table for DM messages (private, should be encrypted)
    const tableDmsPromise = prepareTable(identity, entry.uuid, tableSchemaDMs);
    // Setup Table for new DMs (Server can write)
    const tableReceivedDmsPromise = prepareTable(identity, entry.uuid, tableSchemaReceivedDms);

    // Await Promises
    const tableServerList = await tableServerListPromise;
    console.log("Table ServerList: ", tableServerList);
    const tableFriendList = await tableFriendListPromise;
    console.log("Table FriendList: ", tableFriendList);
    const tableFriendRequests = await tableFriendRequestsPromise;
    console.log("Table FriendRequests: ", tableFriendRequests);
    const tableDms = await tableDmsPromise;
    console.log("Table DMs: ", tableDms);
    const tableReceivedDms = await tableReceivedDmsPromise;
    console.log("Table Received DMs: ", tableReceivedDms);

    // Get User Info and Check Table Paths
    const userInfo = await getUserInfo(true);
    if (userInfo != null) {
        const friendRequestTablePath = await getTablePath(identity, entry.uuid, tableFriendRequests.tableUuid!);
        const receivedDmsTablePath = await getTablePath(identity, entry.uuid, tableReceivedDms.tableUuid!);

        // Send IRC Server the Table Paths if needed
        if (userInfo.friendRequestTablePath != friendRequestTablePath || userInfo.receivedDmsTablePath != receivedDmsTablePath) {
            userInfo.friendRequestTablePath = friendRequestTablePath;
            userInfo.receivedDmsTablePath = receivedDmsTablePath;
            await setUserInfo(userInfo);
        }
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

    // Does not exist!
    // TODO: Link IRC Server to FIS Identity Entry

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
        if (!compArrays(maybeEntry.handlesWithReadPerms!, baseTable.handlesWithReadPerms!)) {
            maybeEntry.handlesWithReadPerms = baseTable.handlesWithReadPerms;
            updated = true;
        }
        if (!compArrays(maybeEntry.handlesWithWritePerms!, baseTable.handlesWithWritePerms!)) {
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








const compArrays = (arr1: unknown[], arr2: unknown[]): boolean => {
    // Early exit: Different lengths
    if (arr1.length !== arr2.length) return false;

    // Sort arrays by stringifying elements (with sorted keys)
    const sortFn = (a: unknown, b: unknown) => {
        const strA = JSON.stringify(a, Object.keys(a as object).sort());
        const strB = JSON.stringify(b, Object.keys(b as object).sort());
        return strA.localeCompare(strB);
    };

    const sortedArr1 = [...arr1].sort(sortFn);
    const sortedArr2 = [...arr2].sort(sortFn);

    // Compare sorted arrays
    return JSON.stringify(sortedArr1) === JSON.stringify(sortedArr2);
};