'use client';

import {LocalChatMessage, LocalRoomData} from "@/libs/dtos";
import LazyMedia from "@/app/components/lazy-media/component";
import styles from "@/app/user/home/page.module.css";
import {PublicGoofyIrcData} from "@/libs/service-dtos";
import {GlobalState} from "@/libs/global-state";

export function renderMessage(msg: LocalChatMessage, currRoom: LocalRoomData | null, allPublicData: Map<string, PublicGoofyIrcData | null>) {
    const timeStamp = <span title={JSON.stringify(msg, null, 4)}>{msg.sigValid ? "" : "⚠ "}[{msg.timestamp.toLocaleTimeString()}] </span>;
    // TODO: Expire Cache after a bit, like an hour or whatever?
    const pub = allPublicData.get(msg.handle) ?? null;
    const pfpStuff = (pub == null || pub.pfpPath == null) ? <></> : <LazyMedia mediaPath={pub.pfpPath} roomServerUrl={pub.serverUrl} enforceSize={"1rem"}></LazyMedia>;
    const handle = <b title={JSON.stringify(pub, null, 4)}>&nbsp;{msg.handle}: </b>;
    const extra = msg.msgObj.filePaths.length == 0 ? <></> : (<div>{
        msg.msgObj.filePaths.map((path, idx) => (<div key={idx}><LazyMedia mediaPath={path} roomServerUrl={currRoom?.server.serverUrl ?? ""} enforceSize={null}></LazyMedia></div>))
    }</div>);
    return (<li className={`${styles.MainChatEntry} ${msg.sigValid ? "" : styles.InvalidChatEntry} ${msg.isRealMessage ? "" : styles.SystemChatEntry}`} key={msg.uuid}>
        {timeStamp}
        {pfpStuff}
        {handle}
        {msg.msgObj.msg}
        {extra}
    </li>);
}

export function renderRoomTyping(currRoom: LocalRoomData | null) {
    if (currRoom == null || currRoom.room.members == null || currRoom.room.memberStatus == null)
        return <></>;

    const typingPpl = [];
    for (let idx = 0; idx < currRoom.room.members.length; idx++)
        if (currRoom.room.memberStatus[idx].typingInRoom == currRoom.room.name &&
            currRoom.room.members[idx] != GlobalState.handle)
            typingPpl.push(currRoom.room.members[idx]);

    if (typingPpl.length == 0)
        return <></>;

    return (<>{typingPpl.join(", ")} {typingPpl.length == 1 ? "is" : "are"} typing...</>);
}

export  function canJoinRoom(room: LocalRoomData) {
    return room.room.allowJoining; // TODO: Check for guest behaviour! (allowGuest join if we are guests)
}

export function isMemberOfRoom(room: LocalRoomData, rooms: LocalRoomData[]): boolean {
    return rooms.find((r) => r.room.name == room.room.name && r.server.serverUrl == room.server.serverUrl) != null;
}

// eslint-disable-next-line @typescript-eslint/no-unsafe-function-type
export function renderRoomEntry(room: LocalRoomData, myRooms: LocalRoomData[], currRoom: LocalRoomData | null, unreadCountForRoom: number, viewCallback: Function, joinCallback: Function) {
    const key = `${room.room.name}_${room.server.serverUrl}`;
    const text = (room.sameRoomNameInDiffServer) ? `${room.room.name} (${room.server.serverName})` : room.room.name;
    const isMember = isMemberOfRoom(room, myRooms);
    const canJoin = canJoinRoom(room);
    // TODO: Remove const extra = isMember ? <button onClick={() => {setAndLoadCurrentRoom(room).then()}}>View</button> : ( canJoin ? <button onClick={() => {joinRoom(room).then()}}>Join</button> : <></>);
    const extra = isMember ? <button onClick={viewCallback as never}>View</button> : ( canJoin ? <button onClick={joinCallback as never}>Join</button> : <></>);
    const isCurrRoom = currRoom?.server.serverUrl == room.server.serverUrl && currRoom?.room.name == room.room.name;
    const unread = unreadCountForRoom; // TODO: Remove (room.room.name, room.server.serverUrl);

    return (<li key={key} title={JSON.stringify(room.room, null, 4)}>{isCurrRoom ? <b>{text}</b> : text} {unread == 0 ? "" : `(${unread})`} {extra}</li>);
}

export function renderRoomDetails(currRoom: LocalRoomData | null) {
    if (currRoom == null)
        return <></>;

    const onlineCount = currRoom.room.members == null ? 0 : currRoom.room.members!.filter((_, idx) => currRoom.room.memberStatus![idx].isOnline)?.length;
    const onlineMembers: string[] = currRoom.room.members == null ? [] : currRoom.room.members!.filter((_, idx) => currRoom.room.memberStatus![idx].isOnline);
    const offlineMembers: string[] = currRoom.room.members == null ? [] : currRoom.room.members!.filter((_, idx) => !currRoom.room.memberStatus![idx].isOnline);

    return <div>
        <p>{currRoom.room.description}</p>
        <p><span title={`Online: ${onlineMembers.join(", ")}\nOffline: ${offlineMembers.join(", ")}`}>Members: {onlineCount}/{currRoom.room.memberCount}</span> (Limit: {currRoom.room.userLimit}), Created by: {currRoom.room.createdByHandle}</p>
    </div>
}