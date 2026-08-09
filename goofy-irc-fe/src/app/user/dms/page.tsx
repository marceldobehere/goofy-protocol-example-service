'use client';

import styles from "./page.module.css";
import Link from "next/link";
import {GlobalState, useGlobalState} from "@/libs/global-state";
import {logout} from "@/libs/auth";
import {createServerManager, WsServerManager} from "@/libs/ws";
import {useRef, useState} from "react";
import {ChatRoomDto, LocalServerData, WsGenericEv} from "@/libs/dtos";
import {getBaseServerUrl} from "@/libs/auth-store";
import { getAuth} from "@/libs/req";
import {
    actOnReceivedFriendRequests,
    addStoredIrcServerIfDoesntExist,
    checkAllFriendStuff,
    findPublicDataForHandle,
    getFriendList,
    getFriendRequestList,
    getStoredIrcServerList,
    LocalMember,
    prepFisUtils,
    sendFriendRequest,
    unfriend
} from "@/app/user/fis-utils";
import {PublicGoofyIrcData} from "@/libs/service-dtos";

export default function Page() {
    const [defaultServer, setDefaultServer] = useState<LocalServerData | null>(null);
    const [serverList, setServerList] = useState<LocalServerData[]>([]);
    const [allMembers, setAllMembers] = useState<LocalMember[]>([]);
    const [allFriendRequests, setAllFriendRequests] = useState<LocalMember[]>([]);
    const [allFriends, setAllFriends] = useState<LocalMember[]>([]);

    // const [currentMsgText, setCurrentMsgText] = useState<string>("");
    // const [allUnreadMsgs, setAllUnreadMsgs] = useState<Map<string, number>>(new Map());
    // const [allMsgs, setAllMsgs] = useState<Map<string, LocalChatMessage[]>>(new Map());
    // const [allPublicData, setAllPublicData] = useState<Map<string, PublicGoofyIrcData | null>>(new Map());
    // const [forceRender, setForceRender] = useState<number>(0);
    //
    // // Some cursed stuff, dw abt it
    // const lastTypingRoom = useRef<LocalRoomData | null>(null);
    // const lastTypingTime = useRef<number | null>(null);
    // const typeHandler = useRef(updateMyTyping);
    const msgHandler = useRef(handleMessage);
    // const chatUl = useRef<HTMLUListElement>(null);
    //
    // const [currRoom, setCurrRoom] = useState<LocalRoomData | null>(null);
    // const [currMsgs, setCurrMsgs] = useState<LocalChatMessage[]>([]);
    //
    // const [myRoomList, setMyRoomList] = useState<LocalRoomData[]>([]);
    // const [availableRoomList, setAvailableRoomList] = useState<LocalRoomData[]>([]);
    //
    // Creates a Server Entry and sets up the WS
    async function createServerEntry(serverUrl: string, serverName: string): Promise<LocalServerData> {
        const ws = await createServerManager(serverUrl);

        // Message Handler
        await ws.attachRawWsMessageHandler((msg) => {
            msgHandler.current(serverName, ws, msg).then()
        });

        return {serverUrl, serverName, ws};
    }
    //
    // function getOrCreateMsgListForRoom(roomName: string, serverUrl: string): LocalChatMessage[] {
    //     const key = `${roomName}@${serverUrl}`;
    //     if (!allMsgs.has(key)) {
    //         allMsgs.set(key, []);
    //         allUnreadMsgs.set(key, 0);
    //     }
    //     return allMsgs.get(key)!;
    // }
    //
    // function unreadCountForRoom(roomName: string, serverUrl: string, setVal: "IGNORE" | "RESET" | "INC" = "IGNORE"): number {
    //     const key = `${roomName}@${serverUrl}`;
    //     if (setVal == "IGNORE")
    //         return allUnreadMsgs.get(key) ?? 0;
    //     else if (setVal == "RESET") {
    //         allUnreadMsgs.set(key, 0);
    //         return 0;
    //     } else if (setVal == "INC") {
    //         const val = allUnreadMsgs.get(key) ?? 0;
    //         allUnreadMsgs.set(key, val + 1);
    //         return val + 1;
    //     }
    //
    //     return 0;
    // }
    //
    // // Get Info
    // async function fetchInfoForHandle(handle: string, ircServerBase: string) {
    //     if (allPublicData.has(handle))
    //         return;
    //
    //     // console.debug(`Fetching Public Data for ${handle} on ${ircServerBase}...`);
    //     const info = await findPublicDataForHandle(handle, ircServerBase);
    //     console.debug(`Fetched Public Data for ${handle} on ${ircServerBase}:`, info);
    //     allPublicData.set(handle, info);
    // }
    //
    // Message handler that needs to be called indirectly (using Ref) to still have the currentState
    async function handleMessage(serverName: string, ws: WsServerManager, msg: string) {
        const genEv: WsGenericEv = JSON.parse(msg);
        if (genEv.evType == null)
            return;
        console.debug(`WS GOT [${serverName}]:`, genEv);

        if (genEv.evType == "NEW_FRIEND_REQUEST") {
            await getMembersAndDmsAndFriendRequests();
        }

        // if (genEv.evType == "ERROR") {
        //     console.error(`WS GOT ERR [${serverName}]:`, genEv);
        // } else if (genEv.evType == "UPDATE_ROOM_LIST") {
        //     await loadRoomListData();
        // } else if (genEv.evType == "UPDATE_ROOM_DATA") {
        //     const roomName = (genEv as WsUpdateRoomData).roomName;
        //     await refreshRoom(roomName, ws.serverUrl);
        // } else if (genEv.evType == "RECEIVE_MSG") {
        //     const msgEv = genEv as WsReceiveMsg;
        //
        //     // Lookup identity
        //     const lookup: IrcHandleLookupDto = await lookUpHandle(msgEv.senderHandle, ws.serverUrl);
        //
        //     // Fetch Data
        //     await fetchInfoForHandle(msgEv.senderHandle, ws.serverUrl);
        //
        //     // Validate
        //     const valid = await asymmVerifyObj(JSON.parse(msgEv.msgObj), msgEv.sig, parsePublicSplitKey(lookup.pubKey));
        //     if (!valid)
        //         console.warn(`Invalid signature for message from ${msgEv.senderHandle} in room ${msgEv.roomName} on server ${serverName}`, msgEv, lookup);
        //
        //     // Create Obj
        //     const msg: LocalChatMessage = {
        //         msgObj: JSON.parse(msgEv.msgObj),
        //         handle: msgEv.senderHandle,
        //         uuid: crypto.randomUUID(),
        //         // eslint-disable-next-line react-hooks/purity
        //         timestamp: new Date(Date.now()),
        //         sig: msgEv.sig,
        //         sigValid: valid,
        //         isRealMessage: true
        //     };
        //
        //     const msgs = getOrCreateMsgListForRoom(msgEv.roomName, ws.serverUrl);
        //     msgs.push(msg);
        //     if (msgs == currMsgs) {
        //         unreadCountForRoom(msgEv.roomName, ws.serverUrl, "RESET");
        //         setTimeout(() => {
        //             chatUl.current?.scrollTo({top: chatUl.current.scrollHeight, behavior: "smooth"});
        //         }, 150);
        //     }
        //     else
        //         unreadCountForRoom(msgEv.roomName, ws.serverUrl, "INC");
        //     setForceRender(forceRender + 1);
        // } else if (genEv.evType == "UPDATE_IDENTITY") {
        //     const handle = (genEv as WsUpdateIdentity).handle;
        //     await refreshPublicData(handle, ws.serverUrl);
        // }
    }
    // eslint-disable-next-line react-hooks/refs
    msgHandler.current = handleMessage;
    //
    // async function createSystemMessage(msg: string, roomName: string, serverUrl: string) {
    //     await createFakeMessage({msg, filePaths: []}, roomName, serverUrl);
    // }
    //
    // async function createFakeMessage(msgObj: ChatMessageDto, roomName: string, serverUrl: string) {
    //     // Create Obj
    //     const msg: LocalChatMessage = {
    //         msgObj,
    //         handle: "[SYSTEM]",
    //         uuid: crypto.randomUUID(),
    //         // eslint-disable-next-line react-hooks/purity
    //         timestamp: new Date(Date.now()),
    //         sig: "",
    //         sigValid: true,
    //         isRealMessage: false
    //     };
    //
    //     const msgs = getOrCreateMsgListForRoom(roomName, serverUrl);
    //     msgs.push(msg);
    //     if (msgs == currMsgs) {
    //         setTimeout(() => {
    //             chatUl.current?.scrollTo({top: chatUl.current.scrollHeight, behavior: "smooth"});
    //         }, 150);
    //     }
    //     setForceRender(forceRender + 1);
    // }

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

    async function getMembersAndDmsAndFriendRequests(servers: LocalServerData[] = serverList) {
        if (servers == null || servers.length === 0)
            return [];

        // TODO: maybe remove?
        await checkAllFriendStuff();

        const allMembers = await getAllMembers(servers);
        setAllFriends(await getFriendList(allMembers));
        setAllFriendRequests(await getFriendRequestList());
    }

    async function getAllMembers(servers: LocalServerData[] = serverList): Promise<LocalMember[]> {
        if (servers == null || servers.length === 0 || GlobalState.handle == null)
            return [];

        const memberList: LocalMember[] = [];
        const memberSet: Set<string> = new Set();
        const lostMemberSet: Set<string> = new Set();
        memberSet.add(GlobalState.handle);

        for (const server of servers) {
            try {
                const list = await getAuth<ChatRoomDto[]>(server.serverUrl + "/api/chatroom/list/my");
                list.forEach(room => {
                    if (room.allowGuests)
                        room.members?.forEach((member) => {
                            if (!memberSet.has(member))
                                lostMemberSet.add(member);
                        })
                    else
                        room.members?.forEach(member => {
                            if (!memberSet.has(member)) {
                                memberSet.add(member);
                                memberList.push({
                                    handle: member,
                                    serverUrl: server.serverUrl,
                                })
                            }
                        })
                })
            } catch (e) {
                console.error(`Failed to load my rooms from server ${server.serverName} (${server.serverUrl}):`, e);
            }
        }

        // Remove members from lostSet
        for (const member of [...lostMemberSet.keys()])
            if (memberSet.has(member))
                lostMemberSet.delete(member);

        // try looking up lostMemberSet
        for (const member of lostMemberSet.keys()) {
            let foundInfo: PublicGoofyIrcData | null = null;
            for (const server of servers) {
                try {
                    const info = await findPublicDataForHandle(member, server.serverUrl);
                    if (info) {
                        foundInfo = info;
                        break;
                    }
                } catch {

                }
            }
            if (foundInfo) {
                memberList.push({
                    handle: member,
                    serverUrl: foundInfo.serverUrl
                })
            }
        }

        // console.log("All members list:", memberList);
        setAllMembers(memberList);
        return memberList;
    }

    async function sendFriendReq(member: LocalMember) {
        try {
            await sendFriendRequest(member);
        } catch (e) {
            console.error(e);
            alert(`Failed to send Friend request for ${member.serverUrl}: ` + (e as Error).message);
        }
    }

    async function acceptFriendReq(member: LocalMember) {
        await actOnReceivedFriendRequests(member, "ACCEPT");
        await getMembersAndDmsAndFriendRequests();

    }

    async function denyFriendReq(member: LocalMember) {
        await actOnReceivedFriendRequests(member, "DENY");
        await getMembersAndDmsAndFriendRequests();
    }

    async function unfriendReq(member: LocalMember) {
        await unfriend(member);
        await getMembersAndDmsAndFriendRequests();
    }

    async function openFriendDm(member: LocalMember) {

    }

    useGlobalState(true, false, "NONE", async () => {
        // setAllMsgs(new Map());
        // setAllUnreadMsgs(new Map());
        // setAllPublicData(new Map());

        await prepFisUtils();

        // Get Server List
        const sList = await loadServerList();

        // Get All Stuff
        await getMembersAndDmsAndFriendRequests(sList);
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
                <h2 className={styles.Title}>DMs</h2>

                <br/>
                <p>Hello, {GlobalState.handle}!</p><br/>
                <div className={styles.PageButtons}>
                    <button onClick={logout}>Logout</button><br/>
                    {GlobalState.isAdmin ? <Link href="/admin/home">Admin</Link> : null}
                    <Link href={"/"}>Index</Link>
                    <Link href={"/user/home"} target={"_blank"}>Home Page</Link>
                </div>

                <br/><hr/><br/>

                <div className={styles.MainContainer}>
                    <div className={styles.MainSidebar}>
                        <div className={styles.MainSidebarListBlock}>
                            <h3>Friends / DMs</h3>
                            <ul>{allFriends.map((m) => (<li key={m.handle} title={JSON.stringify(m, null, 4)} onClick={() => {openFriendDm(m).then()}}>
                                {m.nickname ?? m.handle} {m.isFriendsRn ? "" : "(Not Friends?)"}<span> &nbsp; </span>
                                <button onClick={() => {unfriendReq(m).then()}}>Unfriend</button>
                            </li>))}</ul>
                        </div>
                        <hr/>
                        <div className={styles.MainSidebarListBlock}>
                            <h3>Received Friend Requests</h3>
                            <ul>{allFriendRequests.map((m) => (<li key={m.handle} title={JSON.stringify(m, null, 4)}>
                                {m.handle}<span> &nbsp; </span>
                                <button onClick={() => {acceptFriendReq(m).then()}}>Accept</button><span> &nbsp; </span>
                                <button onClick={() => {denyFriendReq(m).then()}}>Deny</button>
                            </li>))}</ul>
                        </div>
                        <hr/>
                        <div className={styles.MainSidebarListBlock}>
                            <h3>All Members</h3>
                            <ul>{allMembers.map((m) => (<li key={m.handle} title={JSON.stringify(m, null, 4)}>
                                {m.handle}<span> &nbsp; </span>
                                <button onClick={() => {sendFriendReq(m).then()}}>Add</button>
                            </li>))}</ul>
                        </div>
                        <hr/>
                    </div>

                    <div className={styles.MainChatWindow}>
                        {/*{currRoom == null ? (<></>) : (<>*/}
                        {/*    <div className={styles.MainChatRoomStats}>*/}
                        {/*        <h3>{currRoom?.room.name ?? ""}</h3>*/}
                        {/*        {renderRoomDetails(currRoom)}*/}

                        {/*        {(currRoom.room.createdByHandle != GlobalState.handle) ? (<>*/}
                        {/*            <button onClick={() => {leaveRoom(currRoom).then()}}>Leave Room</button>*/}
                        {/*            <span> &nbsp; </span>*/}
                        {/*        </>) : (<></>)}*/}

                        {/*        {(currRoom.room.createdByHandle == GlobalState.handle || GlobalState.isAdmin) ? (<>*/}
                        {/*            <button>Kick Member</button>*/}
                        {/*            <button>Ban Member</button>*/}
                        {/*            <button>Unban Member</button>*/}
                        {/*            <button onClick={() => {updateRoom(currRoom).then()}}>Update Room Data</button>*/}
                        {/*            <button onClick={() => {deleteRoom(currRoom).then()}}>Delete Room</button>*/}
                        {/*            <span> &nbsp; </span>*/}
                        {/*        </>) : (<></>)}*/}
                        {/*    </div>*/}
                        {/*    <ul ref={chatUl} className={styles.MainChatList}>*/}
                        {/*        {currMsgs.map((r) => renderMessage(r, currRoom, allPublicData))}*/}
                        {/*    </ul>*/}
                        {/*</>)}*/}
                    </div>

                    <div className={styles.MainChatInput}>
                        {/*Stupid ass fix for some reason this works but using the disabled property directly doesn't*/}
                        {/*{currRoom == null ? (<></>) : (<>*/}
                        {/*    <p>{renderRoomTyping(currRoom)}</p>*/}
                        {/*    <textarea value={currentMsgText} placeholder={"Enter a message or paste a file"} onChange={(e) => {*/}
                        {/*        setCurrentMsgText(e.target.value);*/}
                        {/*        if (e.target.value.trim() != "")*/}
                        {/*            updateMyTyping(true).then();*/}
                        {/*    }} onKeyDown={(e) => {*/}
                        {/*        // Seems to not trigger on mobile?*/}
                        {/*        if (e.code == "Enter" && !e.shiftKey) {*/}
                        {/*            updateMyTyping(false).then();*/}
                        {/*            sendMessageToCurrentRoom().then();*/}
                        {/*            e.preventDefault();*/}
                        {/*            return false;*/}
                        {/*        }*/}
                        {/*    }} onPaste={sendPasteMessage as never}></textarea>*/}
                        {/*    <button onClick={sendMessageToCurrentRoom}>Send</button>*/}
                        {/*</>)}*/}
                    </div>
                </div>
            </div>
        </main>
    );
}
