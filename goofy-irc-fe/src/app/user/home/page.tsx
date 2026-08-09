'use client';

import styles from "./page.module.css";
import Link from "next/link";
import {GlobalState, useGlobalState} from "@/libs/global-state";
import {logout, lookUpHandle} from "@/libs/auth";
import {createServerManager, WsServerManager} from "@/libs/ws";
import {useRef, useState} from "react";
import {
    ChatMessageDto,
    ChatRoomDto,
    IrcHandleLookupDto,
    LocalChatMessage,
    LocalRoomData,
    LocalServerData,
    WsGenericEv,
    WsReceiveMsg,
    WsSendMsg, WsUpdateIdentity,
    WsUpdateRoomData,
    WsUpdateTyping
} from "@/libs/dtos";
import {getBaseServerUrl, getKeypair} from "@/libs/auth-store";
import {deleteAuth, getAuth, postAuth, putAuth} from "@/libs/req";
import {asymmSignObj, asymmVerifyObj, parsePublicSplitKey, sha256ToText} from "@/libs/crypto";
import {
    addStoredIrcServer,
    addStoredIrcServerIfDoesntExist, compStrArrays, deleteStoredIrcServer, findPublicDataForHandle,
    getStoredIrcServerList,
    prepFisUtils, setDescription, uploadFisData, uploadPfp
} from "@/app/user/fis-utils";
import {PublicGoofyIrcData} from "@/libs/service-dtos";
import {renderMessage, renderRoomDetails, renderRoomEntry, renderRoomTyping} from "@/app/user/home/render-utils";

export default function Page() {
    const [currentMsgText, setCurrentMsgText] = useState<string>("");
    const [allUnreadMsgs, setAllUnreadMsgs] = useState<Map<string, number>>(new Map());
    const [allMsgs, setAllMsgs] = useState<Map<string, LocalChatMessage[]>>(new Map());
    const [allPublicData, setAllPublicData] = useState<Map<string, PublicGoofyIrcData | null>>(new Map());
    const [defaultServer, setDefaultServer] = useState<LocalServerData | null>(null);
    const [serverList, setServerList] = useState<LocalServerData[]>([]);
    const [forceRender, setForceRender] = useState<number>(0);

    // Some cursed stuff, dw abt it
    const lastTypingRoom = useRef<LocalRoomData | null>(null);
    const lastTypingTime = useRef<number | null>(null);
    const typeHandler = useRef(updateMyTyping);
    const msgHandler = useRef(handleMessage);
    const chatUl = useRef<HTMLUListElement>(null);

    const [currRoom, setCurrRoom] = useState<LocalRoomData | null>(null);
    const [currMsgs, setCurrMsgs] = useState<LocalChatMessage[]>([]);

    const [myRoomList, setMyRoomList] = useState<LocalRoomData[]>([]);
    const [availableRoomList, setAvailableRoomList] = useState<LocalRoomData[]>([]);

    // Creates a Server Entry and sets up the WS
    async function createServerEntry(serverUrl: string, serverName: string): Promise<LocalServerData> {
        const ws = await createServerManager(serverUrl);

        // Message Handler
        await ws.attachRawWsMessageHandler((msg) => {
            msgHandler.current(serverName, ws, msg).then()
        });

        return {serverUrl, serverName, ws};
    }

    function getOrCreateMsgListForRoom(roomName: string, serverUrl: string): LocalChatMessage[] {
        const key = `${roomName}@${serverUrl}`;
        if (!allMsgs.has(key)) {
            allMsgs.set(key, []);
            allUnreadMsgs.set(key, 0);
        }
        return allMsgs.get(key)!;
    }

    function unreadCountForRoom(roomName: string, serverUrl: string, setVal: "IGNORE" | "RESET" | "INC" = "IGNORE"): number {
        const key = `${roomName}@${serverUrl}`;
        if (setVal == "IGNORE")
            return allUnreadMsgs.get(key) ?? 0;
        else if (setVal == "RESET") {
            allUnreadMsgs.set(key, 0);
            return 0;
        } else if (setVal == "INC") {
            const val = allUnreadMsgs.get(key) ?? 0;
            allUnreadMsgs.set(key, val + 1);
            return val + 1;
        }

        return 0;
    }

    // Get Info
    async function fetchInfoForHandle(handle: string, ircServerBase: string) {
        if (allPublicData.has(handle))
            return;

        // console.debug(`Fetching Public Data for ${handle} on ${ircServerBase}...`);
        const info = await findPublicDataForHandle(handle, ircServerBase);
        console.debug(`Fetched Public Data for ${handle} on ${ircServerBase}:`, info);
        allPublicData.set(handle, info);
    }

    // Message handler that needs to be called indirectly (using Ref) to still have the currentState
    async function handleMessage(serverName: string, ws: WsServerManager, msg: string) {
        const genEv: WsGenericEv = JSON.parse(msg);
        if (genEv.evType == null)
            return;
        console.debug(`WS GOT [${serverName}]:`, genEv);

        if (genEv.evType == "ERROR") {
            console.error(`WS GOT ERR [${serverName}]:`, genEv);
        } else if (genEv.evType == "UPDATE_ROOM_LIST") {
            await loadRoomListData();
        } else if (genEv.evType == "UPDATE_ROOM_DATA") {
            const roomName = (genEv as WsUpdateRoomData).roomName;
            await refreshRoom(roomName, ws.serverUrl);
        } else if (genEv.evType == "RECEIVE_MSG") {
            const msgEv = genEv as WsReceiveMsg;

            // Lookup identity
            const lookup: IrcHandleLookupDto = await lookUpHandle(msgEv.senderHandle, ws.serverUrl);

            // Fetch Data
            await fetchInfoForHandle(msgEv.senderHandle, ws.serverUrl);

            // Validate
            const valid = await asymmVerifyObj(JSON.parse(msgEv.msgObj), msgEv.sig, parsePublicSplitKey(lookup.pubKey));
            if (!valid)
                console.warn(`Invalid signature for message from ${msgEv.senderHandle} in room ${msgEv.roomName} on server ${serverName}`, msgEv, lookup);

            // Create Obj
            const msg: LocalChatMessage = {
                msgObj: JSON.parse(msgEv.msgObj),
                handle: msgEv.senderHandle,
                uuid: crypto.randomUUID(),
                // eslint-disable-next-line react-hooks/purity
                timestamp: new Date(Date.now()),
                sig: msgEv.sig,
                sigValid: valid,
                isRealMessage: true
            };

            const msgs = getOrCreateMsgListForRoom(msgEv.roomName, ws.serverUrl);
            msgs.push(msg);
            if (msgs == currMsgs) {
                unreadCountForRoom(msgEv.roomName, ws.serverUrl, "RESET");
                setTimeout(() => {
                    chatUl.current?.scrollTo({top: chatUl.current.scrollHeight, behavior: "smooth"});
                }, 150);
            }
            else
                unreadCountForRoom(msgEv.roomName, ws.serverUrl, "INC");
            setForceRender(forceRender + 1);
        } else if (genEv.evType == "UPDATE_IDENTITY") {
            const handle = (genEv as WsUpdateIdentity).handle;
            await refreshPublicData(handle, ws.serverUrl);
        }
    }
    // eslint-disable-next-line react-hooks/refs
    msgHandler.current = handleMessage;

    async function createSystemMessage(msg: string, roomName: string, serverUrl: string) {
        await createFakeMessage({msg, filePaths: []}, roomName, serverUrl);
    }

    async function createFakeMessage(msgObj: ChatMessageDto, roomName: string, serverUrl: string) {
        // Create Obj
        const msg: LocalChatMessage = {
            msgObj,
            handle: "[SYSTEM]",
            uuid: crypto.randomUUID(),
            // eslint-disable-next-line react-hooks/purity
            timestamp: new Date(Date.now()),
            sig: "",
            sigValid: true,
            isRealMessage: false
        };

        const msgs = getOrCreateMsgListForRoom(roomName, serverUrl);
        msgs.push(msg);
        if (msgs == currMsgs) {
            setTimeout(() => {
                chatUl.current?.scrollTo({top: chatUl.current.scrollHeight, behavior: "smooth"});
            }, 150);
        }
        setForceRender(forceRender + 1);
    }

    async function sendToServer(server: LocalServerData | null, msg: WsGenericEv) {
        if (server == null)
            return;

        await server.ws.sendRawWsMessage(JSON.stringify(msg));
    }

    async function sendToAllServers(msg: WsGenericEv) {
        await Promise.all(serverList.map((s => sendToServer(s, msg))));
    }

    // TODO: Add max timeout if the same text is standing there for 1000 years
    // Typing handler that needs to be called indirectly (using Ref) to still have the currentState
    const TYPING_TIMEOUT = 1500;
    async function updateMyTyping(_typing: boolean | null, checkLoop: boolean = false) {
        const typing = _typing != null ? _typing : currentMsgText.trim() != "";
        // eslint-disable-next-line react-hooks/purity
        const now = Date.now();
        const wasTyping: boolean = lastTypingTime.current != null && now < lastTypingTime.current + TYPING_TIMEOUT;
        const roomChanged: boolean = lastTypingRoom.current != currRoom;
        const lastServer = lastTypingRoom.current?.server ?? null;
        const serverChanged: boolean = currRoom?.server != lastServer;
        lastTypingTime.current = (typing ? now : null);
        lastTypingRoom.current = currRoom;

        // console.debug("TYPING: ", typing, _typing, wasTyping);

        if (!typing) {
            if (wasTyping) {
                await sendToServer(lastServer ?? defaultServer, new WsUpdateTyping(null));
            }
            return;
        }

        if (typing && (!wasTyping || roomChanged)) {
            if (serverChanged)
                await sendToServer(lastServer ?? defaultServer, new WsUpdateTyping(null));

            await sendToServer(currRoom?.server ?? defaultServer, new WsUpdateTyping(currRoom?.room.name ?? null));
        }

        if (typing && !wasTyping || checkLoop)
            setTimeout(() => {
                // console.debug("TYPING CHECK: ", msgInputRef?.current);
                typeHandler.current(null, true).then();
            }, 500);
    }
    // eslint-disable-next-line react-hooks/refs
    typeHandler.current = updateMyTyping;


    async function loadServerList(): Promise<LocalServerData[]> {
        // Prepare Server List
        const baseUrl = await getBaseServerUrl();
        await addStoredIrcServerIfDoesntExist({serverUrl: baseUrl, serverName: "Current Server"});

        // Get List
        const serverList = await getStoredIrcServerList();
        console.debug("Stored Server List: ", serverList);

        // Create Servers
        const serverEntries: LocalServerData[] = [];
        for (const server of serverList) {
            try {
                const serverEntry = await createServerEntry(server.serverUrl, server.serverName);
                serverEntries.push(serverEntry);

                if (serverEntry.serverUrl == baseUrl)
                    setDefaultServer(serverEntry);
            } catch (e) {
                console.error(e);
                alert(`Failed to connect to server ${server.serverName} (${server.serverUrl}). This may indicate that the server is not reachable or that your handle is not known/allowed on this server. Error: ${e}`)
            }
        }

        setServerList(serverEntries);
        return serverEntries;
    }

    async function loadRoomListData(servers: LocalServerData[] = serverList) {
        if (servers.length == 0) {
            setMyRoomList([]);
            setAvailableRoomList([]);
            await setAndLoadCurrentRoom(null, true);
            return;
        }

        // My DMs
        // setDmList([]);

        // My Rooms
        const _myRooms: LocalRoomData[] = [];
        for (const server of servers) {
            try {
                const list = await getAuth<ChatRoomDto[]>(server.serverUrl + "/api/chatroom/list/my");
                list.forEach(room => {
                    _myRooms.push({room, server, sameRoomNameInDiffServer: false});
                })
            } catch (e) {
                console.error(`Failed to load my rooms from server ${server.serverName} (${server.serverUrl}):`, e);
            }
        }
        setMyRoomList(_myRooms);

        // All available Rooms (- My Rooms?)
        const _availableRooms: LocalRoomData[] = [];
        for (const server of servers) {
            try {
                const list = await getAuth<ChatRoomDto[]>(server.serverUrl + "/api/chatroom/list/available");
                list.forEach(room => {
                    _availableRooms.push({room, server, sameRoomNameInDiffServer: false});
                })
            } catch (e) {
                console.error(`Failed to load available rooms from server ${server.serverName} (${server.serverUrl}):`, e);
            }
        }

        // Check all rooms
        const _allRooms: LocalRoomData[] = [..._myRooms, ..._availableRooms];
        for (const room of _allRooms)
            if (_allRooms.find((r) => room.room.name == r.room.name && room.server.serverUrl != r.server.serverUrl) != null)
                room.sameRoomNameInDiffServer = true;

        // Filter out duplicates
        const filter = (room: LocalRoomData) => _myRooms.find(
            (r) => room.room.name == r.room.name && room.server.serverUrl == r.server.serverUrl) == null;
        setAvailableRoomList(_availableRooms.filter(filter));

        // Check if our current room is still there
        if (currRoom != null) {
            const found = _allRooms.find((r) => currRoom.room.name == r.room.name && currRoom.server.serverUrl == r.server.serverUrl);
            if (found == null || found.room.members == null || !found.room.members.includes(GlobalState.handle!))
                await setAndLoadCurrentRoom(null, true);
        }

        // TODO: Delete message cache for deleted rooms
    }

    async function createRoom(server: LocalServerData | null = defaultServer) {
        if (server == null)
            return;

        const name = prompt("Enter a name for the Room (lowercase letters, numbers, and underscores only)");
        if (name == null || name.trim() == "")
            return;

        const description = prompt("Enter a description for the Room (optional)");
        if (description == null)
            return;

        const allowGuests = prompt("Allow guests to join the room? (yes/no)", "no");
        if (allowGuests == null)
            return;

        const password = prompt("Enter a password (optional)");
        if (password == null)
            return;

        const pwHash: string | null = password.trim() == "" ? null : await sha256ToText(password);

        const reqRoom: ChatRoomDto = {
            name,
            description,
            allowGuests: allowGuests == "yes",
            allowJoining: true,
            roomPasswordHash: pwHash
        };

        try {
            const res = await postAuth<ChatRoomDto>(server.serverUrl + "/api/chatroom/create", reqRoom);
            alert("Created Room: " + JSON.stringify(res, null, 2));
            await loadRoomListData();
        } catch (err) {
            console.error(err);
            alert("Failed to create room: " + (err as Error).message);
        }
    }

    async function deleteRoom(room: LocalRoomData | null) {
        if (room == null)
            return;

        if (!confirm("Are you sure you want to delete this room?"))
            return;

        try {
            await deleteAuth(room.server.serverUrl + `/api/chatroom/room/${room.room.name}/delete`);
            alert("Deleted Room!");
            await loadRoomListData();
            await setAndLoadCurrentRoom(null, true);
        } catch (err) {
            console.error(err);
            alert("Failed to create room: " + (err as Error).message);
        }
    }

    async function joinRoom(room: LocalRoomData | null) {
        if (room == null)
            return;

        try {
            const pw = room.room.needsPassword ? prompt("Enter the room password") : "";
            if (pw == null)
                return;
            const pwHash = await sha256ToText(pw);

            await postAuth(room.server.serverUrl + `/api/chatroom/room/${room.room.name}/join?passwordHash=${encodeURIComponent(pwHash)}`, "");
            await loadRoomListData();
        } catch (err) {
            console.info(err);
            alert("Failed to join room: " + (err as Error).message);
        }
    }

    async function leaveRoom(room: LocalRoomData | null) {
        if (room == null)
            return;

        try {
            await postAuth(room.server.serverUrl + `/api/chatroom/room/${room.room.name}/leave`, "");
            await loadRoomListData();
        } catch (err) {
            console.error(err);
            alert("Failed to leave room: " + (err as Error).message);
        }
    }

    async function updateRoom(room: LocalRoomData | null) {
        if (room == null)
            return;

        // TODO: Implement
        const updatedDto = prompt("Enter updated room DTO (JSON)", JSON.stringify(room.room, null, 4));
        if (updatedDto == null || updatedDto.trim() == "")
            return;

        const updatedRoom: ChatRoomDto = JSON.parse(updatedDto);

        try {
            const res: ChatRoomDto = await putAuth(room.server.serverUrl + `/api/chatroom/room/${room.room.name}`, updatedRoom);
            alert("Updated Room: " + JSON.stringify(res, null, 2));
            await loadRoomListData();
        } catch (err) {
            console.error(err);
            alert("Failed to update room: " + (err as Error).message);
        }
    }

    async function addIrcServer() {
        const serverUrl = prompt("Enter a server url");
        if (serverUrl == null || serverUrl.trim()  == "")
            return;

        const serverName = prompt("Enter a server name");
        if (serverName == null || serverName.trim() == "")
            return;

        // Create Entry
        const server = await createServerEntry(serverUrl, serverName);

        // Store in Fis
        await addStoredIrcServer({serverUrl, serverName});

        const sList = [...serverList, server];
        setServerList(sList);
        await loadRoomListData(sList);
    }

    async function removeIrcServer(server: LocalServerData) {
        if (!confirm(`Are you sure you want to remove the server ${server.serverName} (${server.serverUrl})?`))
            return;

        try {
            await server.ws.destroy();
        } catch (err) {
            console.error("Error Closing WS: ", server, err);
        }

        await deleteStoredIrcServer(server.serverUrl);
        const sList = serverList.filter((s) => s.serverUrl != server.serverUrl);
        setServerList(sList);
        await loadRoomListData(sList);
    }

    async function refreshRoom(roomName: string | null, roomServer: string) {
        if (roomName == null || currRoom == null)
            return;

        const foundRoom = myRoomList.find((r) => (roomName == r.room.name && roomServer == r.server.serverUrl));
        await setAndLoadCurrentRoom(foundRoom ?? currRoom, (roomName == currRoom.room.name && roomServer == currRoom.server.serverUrl));
    }

    async function setAndLoadCurrentRoom(_room: LocalRoomData | null, doSet: boolean) {
        if (_room == null) {
            if (doSet) {
                setCurrMsgs([]);
                setCurrRoom(null);
            }
            return;
        }

        try {
            const roomDto = await getAuth<ChatRoomDto>(_room.server.serverUrl + `/api/chatroom/room/${_room.room.name}`);
            const newRoom = {room: roomDto, server: _room.server, sameRoomNameInDiffServer: _room.sameRoomNameInDiffServer};

            if (doSet) {
                unreadCountForRoom(roomDto.name, _room.server.serverUrl, "RESET");
                setCurrMsgs(getOrCreateMsgListForRoom(roomDto.name, _room.server.serverUrl));
                setCurrRoom(newRoom);
                setTimeout(() => {
                    chatUl.current?.scrollTo({top: chatUl.current.scrollHeight, behavior: "smooth"});
                }, 150);
            }

            // Compare Members
            if (_room.room.members != null && roomDto.members != null) {
                // Compare Members
                const memberComp = compStrArrays(_room.room.members, roomDto.members);
                if (!memberComp.identical) {
                    for (const member of memberComp.removedValues)
                        await createSystemMessage(`${member} left the room.`, _room.room.name, _room.server.serverUrl);
                    for (const member of memberComp.addedValues)
                        await createSystemMessage(`${member} joined the room.`, _room.room.name, _room.server.serverUrl);
                }

                // Compare Online Members
                const onlineComp = compStrArrays(
                    _room.room.members!.filter((_, idx) => _room.room.memberStatus![idx].isOnline),
                    roomDto.members!.filter((_, idx) => roomDto.memberStatus![idx].isOnline)
                );

                // Remove Compared Members
                onlineComp.addedValues = onlineComp.addedValues.filter((v) => !memberComp.addedValues.includes(v));
                onlineComp.removedValues = onlineComp.removedValues.filter((v) => !memberComp.removedValues.includes(v));


                if (!onlineComp.identical) {
                    for (const member of onlineComp.removedValues)
                        await createSystemMessage(`${member} disconnected.`, _room.room.name, _room.server.serverUrl);
                    for (const member of onlineComp.addedValues)
                        await createSystemMessage(`${member} connected.`, _room.room.name, _room.server.serverUrl);
                }

                // Other Room updates
                if (_room.room.description != roomDto.description ||
                    _room.room.userLimit != roomDto.userLimit ||
                    _room.room.allowJoining != roomDto.allowJoining ||
                    _room.room.allowGuests != roomDto.allowGuests)
                    await createSystemMessage(`Room details were updated.`, _room.room.name, _room.server.serverUrl);


                // console.debug("COMPARED: ", _room.room, roomDto, memberComp, onlineComp);
            }

            const idx = myRoomList.findIndex((r) => (newRoom.room.name == r.room.name && newRoom.server.serverUrl == r.server.serverUrl));
            if (idx != -1) {
                const tList = [...myRoomList];
                tList[idx] = newRoom;
                setMyRoomList(tList);
                // eslint-disable-next-line react-hooks/immutability
                myRoomList[idx] = newRoom;
            }
        } catch (err) {
            console.error(err);
            alert("Failed to get room data: " + (err as Error).message);
        }
    }

    async function sendMessageToCurrentRoom() {
        if (currentMsgText.trim() == "" || currRoom == null)
            return;

        const msg: ChatMessageDto = {
            msg: currentMsgText.trim(),
            filePaths: []
        }

        // Sign msg obj
        const keypair = await getKeypair();
        const sig = await asymmSignObj(msg, keypair.priv);

        await sendToServer(currRoom.server, new WsSendMsg(currRoom.room.name, JSON.stringify(msg), sig));

        setCurrentMsgText("");
    }

    async function sendPasteMessage(e: ClipboardEvent) {
        const files = e.clipboardData?.files;
        if (files == null || files.length === 0)
            return;
        // console.debug("Files Pasted: ", files);
        if (currRoom == null || !confirm(`Are you sure you want to upload \"${files[0].name}\" to the current room?\nIt will also send this text: ${currentMsgText.trim()}`))
            return;

        const filePaths: string[] = [];
        for (const file of files) {
            try {
                const res = await uploadFisData(file);
                if (res == null) {
                    alert(`Failed to upload file ${file.name}.`);
                    return;
                }
                filePaths.push(res);
            } catch (e) {
                alert(`Failed to upload file ${file.name}.\nError: ${(e as Error).message}`);
                return;
            }
        }

        // console.debug("Uploaded Files: ", filePaths);
        const msg: ChatMessageDto = {
            msg: currentMsgText.trim(),
            filePaths
        }

        // Sign msg obj
        const keypair = await getKeypair();
        const sig = await asymmSignObj(msg, keypair.priv);

        await sendToServer(currRoom.server, new WsSendMsg(currRoom.room.name, JSON.stringify(msg), sig));

        setCurrentMsgText("");
    }

    // Re-Fetch Data
    async function refreshPublicData(handle: string, serverUrl: string) {
        console.log("Refreshing data for: ", handle, " with server: ", serverUrl);
        allPublicData.delete(handle);
        await fetchInfoForHandle(handle, serverUrl);
        setForceRender(forceRender + 1);
    }

    async function sendPublicDataUpdate() {
        if (GlobalState.handle == null)
            return;

        // Send Update
        await sendToAllServers(new WsUpdateIdentity(GlobalState.handle));
    }

    function roomEntryHelper(r: LocalRoomData) {
        return renderRoomEntry(r, myRoomList, currRoom, unreadCountForRoom(r.room.name, r.server.serverUrl),
            () => {setAndLoadCurrentRoom(r, true).then();}, () => {joinRoom(r).then();});
    }

    useGlobalState(true, false, "NONE", async () => {
        setAllMsgs(new Map());
        setAllUnreadMsgs(new Map());
        setAllPublicData(new Map());

        await prepFisUtils();

        // Get Server List
        const sList = await loadServerList();

        // Get Room List
        await loadRoomListData(sList);
    });

    // TODO: Improve Styling + Add Mobile Support
    return (
        <main>
            <dialog id={"fis-dialog"} className={styles.FisRedirectPopup}>
                <button id={"fis-dialog-cancel"} style={{position: "absolute", top: "1rem", right: "1rem", fontSize: "1.2rem", width: "1.6rem"}}>X</button>
                <h2>Update Public Data</h2>
                <br/>
                <p>
                    You can only update your public identity data inside your FIS Client!<br/>
                    Press Enter / Click the link below to get redirected to the FIS Frontend with the Update set.<br/>
                    The FIS page sadly cant close itself automatically, so you will just need to close it.<br/><br/>
                    (This dialog will close when you update the data, cancel it or automatically after ~50s)
                </p>
                <br/><br/>
                <div style={{margin: "auto", textAlign: "center"}}><a id={"fis-dialog-href"} target={"_blank"}>Open FIS Frontend</a></div>
            </dialog>
            <div className={styles.PageContainer}>
                <h2 className={styles.Title}>Home / Live IRC Chat</h2>

                <br/>
                <p>Hello, {GlobalState.handle}!</p><br/>
                <div>
                    <button onClick={() => {uploadPfp(false).then(sendPublicDataUpdate)}}>Set PFP</button><span> &nbsp; </span>
                    <button onClick={() => {uploadPfp(true).then(sendPublicDataUpdate)}}>Reset PFP</button><span> &nbsp; </span>
                    <button onClick={() => {setDescription().then(sendPublicDataUpdate)}}>Set Description</button>
                </div>
                <br/>
                <div className={styles.PageButtons}>
                    <button onClick={logout}>Logout</button><br/>
                    {GlobalState.isAdmin ? <Link href="/admin/home">Admin</Link> : null}
                    <Link href={"/"}>Index</Link>
                    <Link href={"/user/dms"} target={"_blank"}>DMs</Link>
                </div>

                <br/><hr/><br/>

                <div className={styles.MainContainer}>
                    <div className={styles.MainSidebar}>
                        <div className={styles.MainSidebarListBlock}>
                            <h3>Servers</h3>
                            <ul>{serverList.map((s) => (<li key={s.serverUrl} title={JSON.stringify(s, null, 4)}>{s.serverName} &nbsp; <button onClick={() => {removeIrcServer(s).then()}}>X</button></li>))}</ul>
                            <button onClick={addIrcServer}>Add Goofy IRC Server</button>
                        </div>
                        <hr/>
                        <div className={styles.MainSidebarListBlock}>
                            <h3>My Rooms</h3>
                            {/* eslint-disable-next-line react-hooks/refs */}
                            <ul>{myRoomList.map((r) => roomEntryHelper(r))}</ul>
                            <button onClick={() => {createRoom().then()}}>Create Room</button>
                        </div>
                        <hr/>
                        <div className={styles.MainSidebarListBlock}>
                            <h3>All Rooms</h3>
                            {/* eslint-disable-next-line react-hooks/refs */}
                            <ul>{availableRoomList.map((r) => roomEntryHelper(r))}</ul>
                        </div>
                    </div>

                    <div className={styles.MainChatWindow}>
                        {currRoom == null ? (<></>) : (<>
                            <div className={styles.MainChatRoomStats}>
                                <h3>{currRoom?.room.name ?? ""}</h3>
                                {renderRoomDetails(currRoom)}

                                {(currRoom.room.createdByHandle != GlobalState.handle) ? (<>
                                    <button onClick={() => {leaveRoom(currRoom).then()}}>Leave Room</button>
                                    <span> &nbsp; </span>
                                </>) : (<></>)}

                                {(currRoom.room.createdByHandle == GlobalState.handle || GlobalState.isAdmin) ? (<>
                                    <button>Kick Member</button>
                                    <button>Ban Member</button>
                                    <button>Unban Member</button>
                                    <button onClick={() => {updateRoom(currRoom).then()}}>Update Room Data</button>
                                    <button onClick={() => {deleteRoom(currRoom).then()}}>Delete Room</button>
                                    <span> &nbsp; </span>
                                </>) : (<></>)}
                            </div>
                            <ul ref={chatUl} className={styles.MainChatList}>
                                {currMsgs.map((r) => renderMessage(r, currRoom, allPublicData))}
                            </ul>
                        </>)}
                    </div>

                    <div className={styles.MainChatInput}>
                        {/*Stupid ass fix for some reason this works but using the disabled property directly doesn't*/}
                        {currRoom == null ? (<></>) : (<>
                            <p>{renderRoomTyping(currRoom)}</p>
                            <textarea value={currentMsgText} placeholder={"Enter a message or paste a file"} onChange={(e) => {
                                setCurrentMsgText(e.target.value);
                                if (e.target.value.trim() != "")
                                    updateMyTyping(true).then();
                            }} onKeyDown={(e) => {
                                // Seems to not trigger on mobile?
                                if (e.code == "Enter" && !e.shiftKey) {
                                    updateMyTyping(false).then();
                                    sendMessageToCurrentRoom().then();
                                    e.preventDefault();
                                    return false;
                                }
                            }} onPaste={sendPasteMessage as never}></textarea>
                            <button onClick={sendMessageToCurrentRoom}>Send</button>
                        </>)}
                    </div>
                </div>
            </div>
        </main>
    );
}
