'use client';

import styles from "./page.module.css";
import {LocalChatMessage} from "@/libs/dtos";
import {LocalDmChat} from "@/app/user/fis-utils";
import {PublicGoofyIrcData} from "@/libs/service-dtos";
import LazyMedia from "@/app/components/lazy-media/component";

export function renderMessage(msg: LocalChatMessage, chat: LocalDmChat | null, allPublicData: Map<string, PublicGoofyIrcData | null>) {
    const timeStamp = <span title={JSON.stringify(msg, null, 4)}>{msg.sigValid ? "" : "⚠ "}[{msg.timestamp.toLocaleTimeString()}] </span>;
    // TODO: Expire Cache after a bit, like an hour or whatever?
    const pub = allPublicData.get(msg.handle) ?? null;
    const pfpStuff = (pub == null || pub.pfpPath == null) ? <></> : <LazyMedia mediaPath={pub.pfpPath} roomServerUrl={pub.serverUrl} enforceSize={"1rem"}></LazyMedia>;
    const handle = <b title={JSON.stringify(pub, null, 4)}>&nbsp;{(msg.handle == chat?.member.handle ? chat.member.nickname ?? chat.member.handle : msg.handle)}: </b>;
    const extra = msg.msgObj.filePaths.length == 0 ? <></> : (<div>{
        msg.msgObj.filePaths.map((path, idx) => (<div key={idx}><LazyMedia mediaPath={path} roomServerUrl={chat?.member.serverUrl ?? ""} enforceSize={null}></LazyMedia></div>))
    }</div>);
    return (<li className={`${styles.MainChatEntry} ${msg.sigValid ? "" : styles.InvalidChatEntry} ${msg.isRealMessage ? "" : styles.SystemChatEntry}`} key={msg.uuid}>
        {timeStamp}
        {pfpStuff}
        {handle}
        {msg.msgObj.msg}
        {extra}
    </li>);
}