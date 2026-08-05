'use client';

import {createServiceEntry, getServiceEntries} from "@/libs/service-req";
import {getBaseServerUrl, getKeypair} from "@/libs/auth-store";
import {IdentityAsymmFullKeyPair} from "@/libs/auth";
import {ServiceEntryDto} from "@/libs/service-dtos";

const SERVICE_NAME = "DEMO Goofy IRC";

// TODO: Init FIS Storage & DB
// TODO: have the FIS logic for data storage
// TODO: Support Storing Room Messages?
// TODO: Manage Concurrent Message Handling with Insert Locks
export async function prepFisUtils() {
    console.log("FIS Utils init");

    // Prepare Service Entry
    const identity = await getKeypair();
    const entry = await prepareServiceEntry(identity, SERVICE_NAME);
    console.log("Entry: ", entry);


    // // Test Upload
    // console.log("Test Upload");
    // const res = await uploadBucketEntry(identity, entry.uuid);
    // console.log("Bucket Entry: ", res);


    // Setup Table for Connected Server List (private, can be encrypted, doesnt need to i think)

    // Setup Table for Friend List (private, should be encrypted?)
    // Setup Table for DM messages (private, should be encrypted)

    // Setup Table for Friend Requests (Server can write)
    // Setup Table for new DMs (Server can write)


    // If first setup
    // Tell IRC Server about the "new DMs" and "Friend Request" tables so it can use them


    // Lock "New DMs" Table, process messages, unlock Table
    // Update DM Status (unread messages, etc.)


    // Display Friend Requests, doesn't need processing
}

// Get/Create Service Entry with Name
async function prepareServiceEntry(identity: IdentityAsymmFullKeyPair, name: string): Promise<ServiceEntryDto> {
    const entries = await getServiceEntries(identity);
    console.log("Service Entries: ", entries);

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

// Get Table with Name

// Create Table with Name