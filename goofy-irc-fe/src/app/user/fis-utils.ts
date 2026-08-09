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
import {getAuth, getFixedAuth, getFixedAuthBytes, postAuth, putFixedAuth} from "@/libs/req";
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

// To let the server send friend requests
const tableSchemaSentFriendRequests: LocalTableStructure = {
    tableName: "sent_friend_requests",
    schemaVersion: 2,
    handlesWithReadPerms: [],
    handlesWithWritePerms: [],
    columns: [{
        colName: "handle",
        type: "VAR_STRING_N", typeSize: 128,
        constraints: ["PRIMARY_KEY", "NOT_NULL"]
    }, {
        colName: "sent_at",
        type: "BIGINT", // just using a number timestamp
        constraints: ["NOT_NULL"]
    },{
        colName: "server_url",
        type: "VAR_STRING_N", typeSize: 300,
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
let tableSentFriendRequests: ServiceTableEntryDto | null = null;
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
    // Setup Table for Sent Friend Requests
    const tableSentFriendRequestsPromise = prepareTable(currIdentity, serviceEntry.uuid, tableSchemaSentFriendRequests);
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
    tableSentFriendRequests = await tableSentFriendRequestsPromise;
    console.debug("TableSentFriendRequests: ", tableSentFriendRequests);
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

    // Check Friend Stuff
    await checkAllFriendStuff();

    // TODO: Check DMs
    // Lock "New DMs" Table, process messages, unlock Table
    // Update DM Status (unread messages, etc.)
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

    const fisDialog: HTMLDialogElement = document.getElementById("fis-dialog") as HTMLDialogElement;
    const fisHref: HTMLLinkElement = document.getElementById("fis-dialog-href") as HTMLLinkElement;
    const fisCancel: HTMLButtonElement = document.getElementById("fis-dialog-cancel") as HTMLButtonElement;

    // Wait for update
    const prom = new Promise((resolve) => {
        console.debug("Waiting for Public FIS Data to be updated...");
        fisCancel.onclick = () => {fisDialog.close(); resolve(null)};
        fisHref.href = resUrl;
        fisDialog.showModal();
        fisHref.focus();
        // window.open(resUrl, "_blank")?.focus();

        const id = setInterval(async () => {
            const data = await getPublicFisData();
            if (JSON.stringify(data.services[PUBLIC_SERVICE_NAME]) == JSON.stringify(newData)) {
                console.debug("Public FIS Data updated successfully");
                clearInterval(id);
                resolve(null);
            }
        }, 1000);
        sleep(50_000).then(() => {
            console.debug("Timeout while waiting for Public FIS Data to be updated");
            clearInterval(id);
            resolve(null);
        })
    });
    await prom;

    if (fisDialog != null)
        fisDialog.close();
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

export interface LocalMember {
    handle: string;
    nickname?: string;
    serverUrl: string;
    isFriendsRn?: boolean;
}

export async function checkAllFriendStuff() {
    await checkReceivedFriendRequests();
    await checkSentFriendRequests();
    await checkFriendList();
}

// TODO: go through each received friend request and see if any of them match our Sent Friend Request Table
// if yes, remove them from both Tables and add to Friend Table
export async function checkReceivedFriendRequests() {
    await prepIfNeeded();

    // Get Sent Requests
    const sentRequests: string[] = [];
    {
        const query: TableSelectDto = {colNames: ["handle"]};
        const res = await queryTable(currIdentity!, serviceEntry!.uuid!, tableSentFriendRequests!.tableUuid!, query);
        for (const row of res.rows)
            sentRequests.push(row[0] as string);
    }

    // Get Received Requests
    const receivedRequests: string[] = [];
    {
        const query: TableSelectDto = {colNames: ["handle"]};
        const res = await queryTable(currIdentity!, serviceEntry!.uuid!, tableFriendRequests!.tableUuid!, query);
        for (const row of res.rows)
            receivedRequests.push(row[0] as string);
    }

    // Check if we received any that we sent
    for (const handle of receivedRequests) {
        if (!sentRequests.includes(handle))
            continue;

        await addAsFriend(handle);

        // Send Update if needed
        const info = await findPublicDataForHandle(handle, await getBaseServerUrl());
        if (info) {
            // Send Update Friends
            await postAuth(`${info.serverUrl}/api/priv/update-friends/${handle}`, "");

        }
    }
}

// TODO: go through each sent friend request and ask if we are friends now
// If yes, remove from Sent Friend Request Table and add to Friend Table
export async function checkSentFriendRequests() {

}

// TODO: go through each friend and ask if we are still friends
// if no, remove from friends list
export async function checkFriendList() {
    // await prepIfNeeded();
    // const query: TableSelectDto = {
    //     colNames: ["handle"]
    // };

    // TODO: Add back but either have some minimum buffer or extra field if they were ever friends
    // const res = await queryTable(currIdentity!, serviceEntry!.uuid!, tableFriendList!.tableUuid!, query);
    // for (const row of res.rows) {
    //     const handle = row[0] as string;
    //     const info = await findPublicDataForHandle(handle, await getBaseServerUrl());
    //     if (info) {
    //         try {
    //             const stillFriends: boolean = await getAuth(`${info.serverUrl}/api/priv/is-friend/${handle}`)
    //             console.log("STILL FRIENDS?", stillFriends);
    //             if (!stillFriends) {
    //                 await removeFriend(handle);
    //             }
    //         } catch (e) {
    //             console.error(`Failed to get Is Friend Info for Handle: ${handle}`, e);
    //         }
    //     } else {
    //         console.error(`Could not find Friend Request Info for: ${handle}`);
    //     }
    // }
}

async function addAsFriend(memberHandle: string) {
    await prepIfNeeded();
    // Add to Friends Table
    // Add to Sent Friend Request Table
    await insertIntoTable(currIdentity!, serviceEntry!.uuid, tableFriendList!.tableUuid!, {
        "handle": memberHandle,
        "nickname": prompt("Enter a nickname for " + memberHandle),
    });

    // Remove from Sent & Received Table
    const query: TableBasicQueryDto = {
        where: {
            type: "C_EQ",
            conditionParts: [
                {type: "COL", colName: "handle"},
                {type: "VAL", value: memberHandle, valueType: "FIXED_STRING_N"}
            ]
        }
    };
    await deleteFromTable(currIdentity!, serviceEntry!.uuid, tableFriendRequests!.tableUuid!, query);
    await deleteFromTable(currIdentity!, serviceEntry!.uuid, tableSentFriendRequests!.tableUuid!, query);
}

// TODO: remove from Friends Table
async function removeFriend(handle: string) {
    await prepIfNeeded();
// Remove from Friends Table
    const query: TableBasicQueryDto = {
        where: {
            type: "C_EQ",
            conditionParts: [
                {type: "COL", colName: "handle"},
                {type: "VAL", value: handle, valueType: "FIXED_STRING_N"}
            ]
        }
    };
    await deleteFromTable(currIdentity!, serviceEntry!.uuid, tableFriendList!.tableUuid!, query);
}

export async function unfriend(member: LocalMember) {
    await prepIfNeeded();
    await removeFriend(member.handle);

    // Send Unfriend Request
    try {
        await postAuth(`${member.serverUrl}/api/priv/unfriend/${member.handle}`, "");

    } catch (e) {
        if (member.isFriendsRn)
            throw e;
        console.log("Silent Unfriend Error Catch:", e);
    }
}

export async function sendFriendRequest(member: LocalMember) {
    await prepIfNeeded();

    // Check Sent Friend Request Table
    {
        const query: TableSelectDto = {colNames: ["handle"]};
        const res = await queryTable(currIdentity!, serviceEntry!.uuid!, tableSentFriendRequests!.tableUuid!, query);
        let shouldRemove = false;
        for (const row of res.rows)
            if (row[0] as string == member.handle) {
                shouldRemove = true;
                break;
            }

        // Delete if needed
        if (shouldRemove) {
            const query: TableBasicQueryDto = {
                where: {
                    type: "C_EQ",
                    conditionParts: [
                        {type: "COL", colName: "handle"},
                        {type: "VAL", value: member.handle, valueType: "FIXED_STRING_N"}
                    ]
                }
            };
            await deleteFromTable(currIdentity!, serviceEntry!.uuid, tableSentFriendRequests!.tableUuid!, query);
        }
    }

    // Send Friend Request
    await postAuth(`${member.serverUrl}/api/priv/friend-request/${member.handle}`, "");

    // Add to Sent Friend Request Table
    await insertIntoTable(currIdentity!, serviceEntry!.uuid, tableSentFriendRequests!.tableUuid!, {
        "handle": member.handle,
        "sent_at": Date.now(),
        "server_url": member.serverUrl,
    });
}

export async function actOnReceivedFriendRequests(member: LocalMember, action: "DELETE" | "DENY" | "ACCEPT") {
    await prepIfNeeded();
    if (action === "DELETE" || action === "DENY") {
        const query: TableBasicQueryDto = {
            where: {
                type: "C_EQ",
                conditionParts: [
                    {type: "COL", colName: "handle"},
                    {type: "VAL", value: member.handle, valueType: "FIXED_STRING_N"}
                ]
            }
        };
        await deleteFromTable(currIdentity!, serviceEntry!.uuid, tableFriendRequests!.tableUuid!, query);

        // TODO: Implement "DENY" ?
    } else if (action === "ACCEPT") {
        await addAsFriend(member.handle);

        // Send Friend Request
        await postAuth(`${member.serverUrl}/api/priv/friend-request/${member.handle}`, "");
    }
}


export async function getFriendList(allMembers: LocalMember[]): Promise<LocalMember[]> {
    await prepIfNeeded();
    const query: TableSelectDto = {
        colNames: ["handle", "nickname"]
    };

    const res = await queryTable(currIdentity!, serviceEntry!.uuid!, tableFriendList!.tableUuid!, query);
    const list: LocalMember[] = [];
    for (const row of res.rows) {
        const foundMember = allMembers.find((m) => m.handle == row[0]);
        if (foundMember) {
            let isFriends: boolean = false;
            try {
                isFriends = await getAuth(`${foundMember.serverUrl}/api/priv/is-friend/${foundMember.handle}`)
            } catch (e) {
                console.error(`Failed to get Is Friend Info for Handle: ${foundMember.handle}`, e);
            }


            list.push({
                handle: foundMember.handle,
                serverUrl: foundMember.serverUrl,
                nickname: row[1] as string,
                isFriendsRn: isFriends
            });
        } else {
            console.error(`Could not find Friend Info for: ${row[0]}`);
        }
    }
    return list;
}

export async function getFriendRequestList(): Promise<LocalMember[]> {
    await prepIfNeeded();
    const query: TableSelectDto = {
        colNames: ["handle"]
    };

    const res = await queryTable(currIdentity!, serviceEntry!.uuid!, tableFriendRequests!.tableUuid!, query);
    const list: LocalMember[] = [];
    for (const row of res.rows) {
        const info = await findPublicDataForHandle(row[0] as string, await getBaseServerUrl());
        if (info) {
            list.push({
                handle: row[0] as string,
                serverUrl: info.serverUrl
            });
        } else {
            console.error(`Could not find Friend Request Info for: ${row[0]}`);
        }
    }
    return list;
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