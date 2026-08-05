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
    WsSendMsg,
    WsUpdateRoomData,
    WsUpdateTyping
} from "@/libs/dtos";
import {getBaseServerUrl, getKeypair} from "@/libs/auth-store";
import {deleteAuth, getAuth, postAuth, putAuth} from "@/libs/req";
import {asymmSignObj, asymmVerifyObj, parsePublicSplitKey, sha256ToText} from "@/libs/crypto";

export default function Page() {
    const [currentMsgText, setCurrentMsgText] = useState<string>("");
    const [allMsgs, setAllMsgs] = useState<Map<string, LocalChatMessage[]>>(new Map());
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

    // const [dmList, setDmList] = useState<string[]>(["TEST"]);
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
        if (!allMsgs.has(key))
            allMsgs.set(key, []);
        return allMsgs.get(key)!;
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
                sigValid: valid
            };

            const msgs = getOrCreateMsgListForRoom(msgEv.roomName, ws.serverUrl);
            msgs.push(msg);
            setForceRender(forceRender + 1);
            if (msgs == currMsgs)
                setTimeout(() => {
                    chatUl.current?.scrollTo({top: chatUl.current.scrollHeight, behavior: "smooth"});
                }, 150);
        }
    }
    // eslint-disable-next-line react-hooks/refs
    msgHandler.current = handleMessage;

    async function sendToServer(server: LocalServerData | null, msg: WsGenericEv) {
        if (server == null)
            return;

        await server.ws.sendRawWsMessage(JSON.stringify(msg));
    }

    // async function sendToAllServers(msg: WsGenericEv) {
    //     await Promise.all(serverList.map((s => sendToServer(s, msg))));
    // }

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


    async function loadServerList(server: LocalServerData | null = defaultServer): Promise<LocalServerData[]> {
        if (server == null)
            return [];

        // Load Servers
        const servers = [server];
        // TODO: Load servers from FIS

        setServerList(servers);
        return servers;
    }

    async function loadRoomListData(servers: LocalServerData[] = serverList) {
        if (servers.length == 0)
            return;

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
                await setAndLoadCurrentRoom(null);
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
            await setAndLoadCurrentRoom(null);
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
        const sList = [...serverList, server];

        setServerList(sList);
        await loadRoomListData(sList);
    }

    async function refreshRoom(roomName: string | null, roomServer: string) {
        if (roomName == null || currRoom == null)
            return;
        if (roomName != currRoom.room.name || roomServer != currRoom.server.serverUrl)
            return;
        await setAndLoadCurrentRoom(currRoom);
    }

    async function setAndLoadCurrentRoom(_room: LocalRoomData | null) {
        if (_room == null) {
            setCurrMsgs([]);
            setCurrRoom(null);
            return;
        }

        try {
            const roomDto = await getAuth<ChatRoomDto>(_room.server.serverUrl + `/api/chatroom/room/${_room.room.name}`);

            setCurrMsgs(getOrCreateMsgListForRoom(roomDto.name, _room.server.serverUrl));
            setCurrRoom({room: roomDto, server: _room.server, sameRoomNameInDiffServer: _room.sameRoomNameInDiffServer});
        } catch (err) {
            console.error(err);
            alert("Failed to get room data: " + (err as Error).message);
        }
    }

    function canJoinRoom(room: LocalRoomData) {
        return room.room.allowJoining; // TODO: Check for guest behaviour! (allowGuest join if we are guests)
    }

    function isMemberOfRoom(room: LocalRoomData, rooms: LocalRoomData[] = myRoomList): boolean {
        return rooms.find((r) => r.room.name == room.room.name && r.server.serverUrl == room.server.serverUrl) != null;
    }

    function renderRoomEntry(room: LocalRoomData) {
        const key = `${room.room.name}_${room.server.serverUrl}`;
        const text = (room.sameRoomNameInDiffServer) ? `${room.room.name} (${room.server.serverName})` : room.room.name;
        const isMember = isMemberOfRoom(room);
        const canJoin = canJoinRoom(room);
        const extra = isMember ? <button onClick={() => {setAndLoadCurrentRoom(room).then()}}>View</button> : ( canJoin ? <button onClick={() => {joinRoom(room).then()}}>Join</button> : <></>);
        const isCurrRoom = currRoom?.server.serverUrl == room.server.serverUrl && currRoom?.room.name == room.room.name;

        return (<li key={key} title={JSON.stringify(room.room, null, 4)}>{isCurrRoom ? <b>{text}</b> : text} {extra}</li>);
    }

    function renderRoomDetails() {
        if (currRoom == null)
            return <></>;

        const onlineCount = currRoom.room.members == null ? 0 : currRoom.room.members!.filter((_, idx) => currRoom.room.memberStatus![idx].isOnline)?.length;

        return <div>
            <p>{currRoom.room.description}</p>
            <p><span title={currRoom.room.members?.join(", ")}>Members: {onlineCount}/{currRoom.room.memberCount}</span> (Limit: {currRoom.room.userLimit}), Created by: {currRoom.room.createdByHandle}</p>
        </div>
    }

    function renderRoomTyping() {
        if (currRoom == null || currRoom.room.members == null || currRoom.room.memberStatus == null)
            return <></>;

        const typingPpl = currRoom.room.memberStatus
            .filter((stat, idx) => stat.typingInRoom != null && currRoom.room.members![idx] != GlobalState.handle)
            .map((_, idx) => currRoom.room.members![idx]);
        if (typingPpl.length == 0)
            return <></>;

        return (<>{typingPpl.join(", ")} {typingPpl.length == 1 ? "is" : "are"} typing...</>);
    }

    function renderMessage(msg: LocalChatMessage) {
        return (<li className={`${styles.MainChatEntry} ${msg.sigValid ? "" : styles.InvalidChatEntry}`} key={msg.uuid}><span title={msg.timestamp.toLocaleString()}>{msg.sigValid ? "" : "⚠ "}[{msg.timestamp.toLocaleTimeString()}] </span><b title={JSON.stringify(msg, null, 4)}>{msg.handle}:</b> {msg.msgObj.msg}</li>);
    }

    async function sendMessageToCurrentRoom() {
        if (currentMsgText.trim() == "" || currRoom == null)
            return;

        // TODO: Add other stuff like signature, etc.
        const msg: ChatMessageDto = {
            msg: currentMsgText.trim(),
        }

        // Sign msg obj
        const keypair = await getKeypair();
        const sig = await asymmSignObj(msg, keypair.priv);

        await sendToServer(currRoom.server, new WsSendMsg(currRoom.room.name, JSON.stringify(msg), sig));

        setCurrentMsgText("");
    }

    useGlobalState(true, false, "NONE", async () => {
        // TODO: Init FIS Storage & DB
        // TODO: have the FIS logic for data storage

        setAllMsgs(new Map());

        // Get Server
        const server = await createServerEntry(await getBaseServerUrl(), "Current Server");
        setDefaultServer(server);

        // Get Server List
        const sList = await loadServerList(server);

        // Get Room List
        await loadRoomListData(sList);
    });

    // TODO: Add Updating Rooms

    return (
        <main>
            <div className={styles.PageContainer}>
                <h2 className={styles.Title}>Home</h2>

                <br/>
                <p>Hello, {GlobalState.handle}! This is the Home Page.</p><br/>
                <div className={styles.PageButtons}>
                    <button onClick={logout}>Logout</button><br/>
                    {GlobalState.isAdmin ? <Link href="/admin/home">Admin</Link> : null}
                    <Link href={"/"}>Index</Link>
                </div>

                <br/><hr/><br/>

                <div className={styles.MainContainer}>
                    <div className={styles.MainSidebar}>
                        <div className={styles.MainSidebarListBlock}>
                            <h3>Servers</h3>
                            <ul>{serverList.map((s) => (<li key={s.serverUrl} title={JSON.stringify(s, null, 4)}>{s.serverName}</li>))}</ul>
                            <button onClick={addIrcServer}>Add Goofy IRC Server</button>
                        </div>
                        <hr/>
                        {/*<div className={styles.MainSidebarListBlock}>*/}
                        {/*    <h3>My DMs</h3>*/}
                        {/*    <ul><li>TODO</li></ul>*/}
                        {/*    <button>Create DM</button>*/}
                        {/*</div>*/}
                        {/*<hr/>*/}
                        <div className={styles.MainSidebarListBlock}>
                            <h3>My Rooms</h3>
                            <ul>{myRoomList.map((r) => renderRoomEntry(r))}</ul>
                            <button onClick={() => {createRoom().then()}}>Create Room</button>
                        </div>
                        <hr/>
                        <div className={styles.MainSidebarListBlock}>
                            <h3>All Rooms</h3>
                            <ul>{availableRoomList.map((r) => renderRoomEntry(r))}</ul>
                        </div>
                    </div>

                    <div className={styles.MainChatWindow}>
                        {currRoom == null ? (<></>) : (<>
                            <div className={styles.MainChatRoomStats}>
                                <h3>{currRoom?.room.name ?? ""}</h3>
                                {renderRoomDetails()}
                                {currRoom.room.createdByHandle == GlobalState.handle ? (<>
                                    <button>Kick Member</button>
                                    <button>Ban Member</button>
                                    <button>Unban Member</button>
                                    <button onClick={() => {updateRoom(currRoom).then()}}>Update Room Data</button>
                                    <button onClick={() => {deleteRoom(currRoom).then()}}>Delete Room</button>
                                </>) : (<>
                                    <button onClick={() => {leaveRoom(currRoom).then()}}>Leave Room</button>
                                </>)}
                            </div>
                            <ul ref={chatUl} className={styles.MainChatList}>
                                {currMsgs.map((r) => renderMessage(r))}
                            </ul>
                        </>)}
                    </div>

                    <div className={styles.MainChatInput}>
                        {/*Stupid ass fix for some reason this works but using the disabled property directly doesn't*/}
                        {currRoom == null ? (<></>) : (<>
                            <p>{renderRoomTyping()}</p>
                            <textarea value={currentMsgText} onChange={(e) => {
                                setCurrentMsgText(e.target.value);
                                if (e.target.value.trim() != "")
                                    updateMyTyping(true).then();
                            }} onKeyDown={(e) => {
                                if (e.code == "Enter" && !e.shiftKey) {
                                    updateMyTyping(false).then();
                                    sendMessageToCurrentRoom().then();
                                    e.preventDefault();
                                    return false;
                                }
                            }}></textarea>
                            <button onClick={sendMessageToCurrentRoom}>Send</button>
                        </>)}
                    </div>
                </div>
            </div>
        </main>
    );
}
