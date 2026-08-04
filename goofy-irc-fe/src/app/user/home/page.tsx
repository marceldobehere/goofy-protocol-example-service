'use client';

import styles from "./page.module.css";
import Link from "next/link";
import {GlobalState, useGlobalState} from "@/libs/global-state";
import {logout} from "@/libs/auth";
import {attachRawWsMessageHandler, initWs, sendRawWsMessage} from "@/libs/ws";

export default function Page() {
    useGlobalState(true, false, "NONE", async () => {
        await initWs();

        await attachRawWsMessageHandler((msg) => {
            console.log("WS GOT:", msg);
        });

        console.log("Sending WS: Hello from Home Page");
        await sendRawWsMessage(JSON.stringify({evType: "SEND_MSG", roomName: "test", msgObj: JSON.stringify({message: "hi"})}));
    });

    // TODO: allow having multiple irc servers and then group the channels by server
    // Also store that list on the FIS
    // TODO: have the FIS logic for data storage

    return (
        <main>
            <div className={styles.MainCont}>
                <h2 className={styles.Title}>Home</h2>

                <br/>
                <p>Hello, {GlobalState.handle}! This is the Home Page.</p>

                <br/><hr/><br/>

                <button onClick={logout}>Logout</button><br/>

                <div className={styles.MainButtons}>
                    {GlobalState.isAdmin ? <Link href="/admin/home">Admin</Link> : null}
                    <Link href={"/"}>Index</Link>
                </div>
            </div>
        </main>
    );
}
