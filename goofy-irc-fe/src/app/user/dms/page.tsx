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
    DmDbMessage,
    DmSendMessage,
    LocalServerData,
    WsGenericEv,
    WsNewDm
} from "@/libs/dtos";
import {getBaseServerUrl, getKeypair} from "@/libs/auth-store";
import {getAuth, postAuth} from "@/libs/req";
import {
    actOnReceivedFriendRequests,
    addStoredIrcServerIfDoesntExist, addToDmTable,
    checkAllFriendStuff, checkReceivedDms,
    findPublicDataForHandle,
    getFriendList,
    getFriendRequestList,
    getStoredIrcServerList, loadDmsChat, LocalDmChat,
    LocalMember,
    prepFisUtils,
    sendFriendRequest,
    unfriend, uploadFisData
} from "@/app/user/fis-utils";
import {PublicGoofyIrcData} from "@/libs/service-dtos";
import {asymmEncryptObj, asymmSignObj, parsePublicSplitKey} from "@/libs/crypto";
import {renderMessage} from "@/app/user/dms/render-utils";

export default function Page() {
    const [currentMsgText, setCurrentMsgText] = useState<string>("");
    const [serverList, setServerList] = useState<LocalServerData[]>([]);
    const [allMembers, setAllMembers] = useState<LocalMember[]>([]);
    const [allFriendRequests, setAllFriendRequests] = useState<LocalMember[]>([]);
    const [allFriends, setAllFriends] = useState<LocalMember[]>([]);
    const [currChat, setCurrChat] = useState<LocalDmChat | null>(null);
    const [allPublicData, _] = useState<Map<string, PublicGoofyIrcData | null>>(new Map());

    const msgHandler = useRef(handleMessage);
    const chatUl = useRef<HTMLUListElement>(null);
    const textArea = useRef<HTMLTextAreaElement>(null);

    // Creates a Server Entry and sets up the WS
    async function createServerEntry(serverUrl: string, serverName: string): Promise<LocalServerData> {
        const ws = await createServerManager(serverUrl);

        // Message Handler
        await ws.attachRawWsMessageHandler((msg) => {
            msgHandler.current(serverName, ws, msg).then()
        });

        return {serverUrl, serverName, ws};
    }

    // Get Info
    async function fetchInfoForHandle(handle: string, ircServerBase: string) {
        // console.debug(`Fetching Public Data for ${handle} on ${ircServerBase}...`);
        const info = await findPublicDataForHandle(handle, ircServerBase, true);
        console.debug(`Fetched Public Data for ${handle} on ${ircServerBase}:`, info);
        allPublicData.set(handle, info);
    }

    // Message handler that needs to be called indirectly (using Ref) to still have the currentState
    async function handleMessage(serverName: string, _: WsServerManager, msg: string) {
        const genEv: WsGenericEv = JSON.parse(msg);
        if (genEv.evType == null)
            return;
        console.debug(`WS GOT [${serverName}]:`, genEv);

        if (genEv.evType == "ERROR") {
            console.error(`WS GOT ERR [${serverName}]:`, genEv);
        } else if (genEv.evType == "NEW_FRIEND_REQUEST") {
            await getMembersAndDmsAndFriendRequests();
        } else if (genEv.evType == "NEW_DM") {
            await checkReceivedDms();

            const dmEv = genEv as WsNewDm;
            if (currChat != null && currChat.member.handle == dmEv.handleFrom) {
                console.debug(`Got new DM from open chat! `, currChat);
                const res = await loadDmsChat(currChat, "LOAD_NEWER");
                setCurrChat(res);

                setTimeout(() => {
                    chatUl.current?.scrollTo({top: chatUl.current.scrollHeight, behavior: "smooth"});
                }, 150);
            }
        }
    }
    // eslint-disable-next-line react-hooks/refs
    msgHandler.current = handleMessage;

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
        const frens = await getFriendList(allMembers);
        setAllFriends(frens);
        setAllFriendRequests(await getFriendRequestList());

        // console.log("CHECK FRIEND STUFF", currChat, frens, frens.filter((f) => f.handle == currChat?.member.handle).length)
        if (frens.filter((f) => f.handle == currChat?.member.handle).length == 0)
            setCurrChat(null);
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
                    const info = await findPublicDataForHandle(member, server.serverUrl, false);
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

    // TODO: Handle when one person is on a diff server and doesn't allow from anyone blegh
    async function acceptFriendReq(member: LocalMember) {
        await actOnReceivedFriendRequests(member, "ACCEPT");
        await getMembersAndDmsAndFriendRequests();

    }

    async function denyFriendReq(member: LocalMember) {
        if (!confirm("Are you sure?"))
            return;

        await actOnReceivedFriendRequests(member, "DENY");
        await getMembersAndDmsAndFriendRequests();
    }

    async function unfriendReq(member: LocalMember) {
        if (!confirm("Are you sure?"))
            return;

        await unfriend(member);
        await getMembersAndDmsAndFriendRequests();
        await postAuth(`/api/priv/update-friends/${GlobalState.handle}`, "");
    }

    async function openFriendDm(member: LocalMember) {
        // console.log("Opening Chat: ", member);

        try {
            await fetchInfoForHandle(member.handle, member.serverUrl);
            await checkReceivedDms();
            const res = await loadDmsChat({member, msgs: [], oldLimitReached: false}, "INITIAL_LOAD");
            // console.log("Opened Chat: ", res);
            setCurrChat(res);
            setTimeout(() => {
                if (textArea.current != null) {
                    textArea.current.disabled = false;
                    textArea.current.focus();
                }
                chatUl.current?.scrollTo({top: chatUl.current.scrollHeight, behavior: "smooth"});
            }, 350);
        } catch (e) {
            console.error(e);
            alert(`Failed to load dmsChat: ${e}`);
            setCurrChat(null);
        }
    }

    async function sendMessageToCurrentRoom() {
        if (currentMsgText.trim() == "" || currChat == null)
            return;

        if (textArea.current != null)
            textArea.current.disabled = true;

        try {
            const msg: ChatMessageDto = {
                msg: currentMsgText.trim(),
                filePaths: []
            }
            await doSendMsg(msg, currChat);

            setCurrentMsgText("");
        } catch (e) {
            console.error(e);
            alert(`Failed to send PasteMessage: ${e}`);
        } finally {
            if (textArea.current != null) {
                textArea.current.disabled = false;
                textArea.current.focus();
            }
        }
    }

    async function sendPasteMessage(e: ClipboardEvent) {
        const _files = e.clipboardData?.files;
        if (_files == null || _files.length === 0)
            return;
        const files = Array.from(_files);
        // console.debug("Files Pasted: ", files);
        if (currChat == null || !confirm(`Are you sure you want to upload \"${files[0].name}\" to the current chat?\nIt will also send this text: ${currentMsgText.trim()}`))
            return;

        if (textArea.current != null)
            textArea.current.disabled = true;

        try {
            const pubInfo = await lookUpHandle(currChat.member.handle, currChat.member.serverUrl);
            const pubKey = parsePublicSplitKey(pubInfo.pubKey);

            const filePaths: string[] = [];
            for (const file of files) {
                try {
                    const res = await uploadFisData(file, pubKey);
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
            await doSendMsg(msg, currChat);

            setCurrentMsgText("");
        } catch (e) {
            console.error(e);
            alert(`Failed to send PasteMessage: ${e}`);
        } finally {
            if (textArea.current != null) {
                textArea.current.disabled = false;
                textArea.current.focus();
            }
        }
    }

    async function doSendMsg(msg: ChatMessageDto, chat: LocalDmChat) {
        try {
            // Create Signature
            const keypair = await getKeypair();
            const sig = await asymmSignObj(msg, keypair.priv);


            // Add it to local DB
            const dmDb: DmDbMessage = {
                msgObj: msg,
                sig,
                sigValid: true,
                handle: GlobalState.handle!,
                isRealMessage: true
            };

            await addToDmTable(dmDb, crypto.randomUUID(), new Date(), chat.member.handle);


            // Send via server
            const dmSend: DmSendMessage = {
                msgObj: msg,
                sig
            };

            const pubInfo = await lookUpHandle(chat.member.handle, chat.member.serverUrl);
            const encDmSend = await asymmEncryptObj(dmSend, parsePublicSplitKey(pubInfo.pubKey));
            await postAuth(`${chat.member.serverUrl}/api/priv/dm/${chat.member.handle}`, encDmSend);


            // Refresh
            if (currChat != null && currChat == chat) {
                const res = await loadDmsChat(currChat, "LOAD_NEWER");
                setCurrChat(res);

                setTimeout(() => {
                    chatUl.current?.scrollTo({top: chatUl.current.scrollHeight, behavior: "smooth"});
                }, 150);
            }
        } catch (e) {
            console.error(e);
            alert(`Failed to send msg: ${(e as Error).message}`);
        }
    }

    useGlobalState(true, false, "NONE", async () => {
        await fetchInfoForHandle(GlobalState.handle!, await getBaseServerUrl());

        await prepFisUtils();

        // Get Server List
        const sList = await loadServerList();

        // Get All Stuff
        await getMembersAndDmsAndFriendRequests(sList);
    });
    // TODO: Add unread to DMs

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
                    <button onClick={logout}>Logout</button>
                    {GlobalState.isAdmin ? <Link href="/admin/home">Admin</Link> : null}
                    <Link href={"/"}>Index</Link>
                    <Link href={"/user/home"} target={"_blank"}>Home Page</Link>
                </div>

                <br/><hr/><br/>

                <div className={styles.MainContainer}>
                    <div className={styles.MainSidebar}>
                        <div className={styles.MainSidebarListBlock}>
                            <h3>Friends / DMs</h3>
                            <ul>{allFriends.map((m) => (<li style={{cursor: "pointer"}} key={m.handle} title={JSON.stringify(m, null, 4)} onClick={() => {openFriendDm(m).then()}}>
                                {currChat != null && currChat.member.handle == m.handle ?
                                    <b>{m.nickname ?? m.handle}</b> :
                                    <>{m.nickname ?? m.handle}</>} {m.isFriendsRn ? "" : " (Not Friends?)"}<span> &nbsp; </span>
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
                        {currChat == null ? (<></>) : (<>
                            <div className={styles.MainChatRoomStats}>
                                <h3>{currChat.member.nickname ?? currChat.member.handle}</h3>
                            </div>
                            <ul ref={chatUl} className={styles.MainChatList}>
                                {currChat.oldLimitReached ? <></> : (<li>
                                    <button style={{margin: "auto", display: "block"}} onClick={async() => {
                                        const res = await loadDmsChat(currChat, "LOAD_OLDER");
                                        setCurrChat(res);}}>Load more</button>
                                </li>)}
                                {currChat.msgs.map((r) => renderMessage(r, currChat, allPublicData))}
                            </ul>
                        </>)}
                    </div>

                    <div className={styles.MainChatInput}>
                        {/*Stupid ass fix for some reason this works but using the disabled property directly doesn't*/}
                        {currChat == null ? (<></>) : (<>
                            <textarea ref={textArea} value={currentMsgText} placeholder={"Enter a message or paste a file"} onChange={(e) => {
                                setCurrentMsgText(e.target.value);
                            }} onKeyDown={(e) => {
                                // Seems to not trigger on mobile?
                                if (e.code == "Enter" && !e.shiftKey) {
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
