'use client';

import styles from "./page.module.css";
import Link from "next/link";

export default function Page() {
    // TODO: Add some kind of guest mode for non-registered users to just use by authenticating
    return (
        <main>
            <div className={styles.MainCont}>
                <h2 className={styles.Title}>Goofy IRC</h2>

                <p className={styles.Introduction}>
                    An example IRC Service for the Goofy Protocol.
                </p>

                <div className={styles.MainButtons}>
                    <Link href={"/guest/login"}>Login</Link>
                    <Link href={"/guest/register"}>Register</Link>
                    <Link href={"/user/home"}>Home</Link>
                </div>
            </div>
        </main>
    );
}
