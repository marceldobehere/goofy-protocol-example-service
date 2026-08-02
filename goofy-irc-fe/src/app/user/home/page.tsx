'use client';

import styles from "./page.module.css";
import Link from "next/link";
import {GlobalState, useGlobalState} from "@/libs/global-state";
import {logout} from "@/libs/auth";

export default function Page() {
    useGlobalState(true, false, "NONE", async () => {});

    // TODO: Styling
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
