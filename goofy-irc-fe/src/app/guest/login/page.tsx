'use client';

import styles from "./page.module.css";
import Link from "next/link";
import {deriveHandleFromPublicSplitKey, parseFullKeypair} from "@/libs/crypto";
import {AsymmFullJsonKeypair, AsymmFullKeyPair} from "@/libs/crypto-types";
import {getStorageMode, saveKeypair, setStorageMode} from "@/libs/auth-store";
import {useRef, useState} from "react";
import {readJsonFile, uploadData} from "@/libs/file-utils";
import {loadLogin} from "@/libs/register";
import {goPath} from "@/libs/go-path";
import {isUser} from "@/libs/auth";
import {GlobalState, useGlobalState} from "@/libs/global-state";

export default function Page() {
    const [usernamePubKey, setUsernamePubKey] = useState<string>("");
    const [derivedHandle, setDerivedHandle] = useState<string | null>(null);
    const [passwordPrivKey, setPasswordPrivKey] = useState<string>("");

    // Autofill checker
    const usernameRef = useRef<HTMLInputElement>(null);
    const passwordRef = useRef<HTMLInputElement>(null);


    useGlobalState(false, false, "NONE", async () => {
        const queryParams = new URLSearchParams(window.location.search);
        const redirect = queryParams.get("redirectAfter");
        if (redirect != null && GlobalState.loggedIn) {
            console.debug(`Redirecting to ${redirect} because already logged in`);
            window.location.href = redirect;
        } else {
            setTimeout(() => {
                console.debug("Checking for autofill on username/password fields");
                const usernameVal = usernameRef.current?.value ?? "";
                const passwordVal = passwordRef.current?.value ?? "";
                if ((usernameVal && !usernamePubKey) || (passwordVal && !passwordPrivKey)) {
                    console.debug("Autofill detected, updating state");
                    updateUsernamePassword(usernameVal, passwordVal).catch(() => {});
                }
            }, 200);
        }
    });

    // TODO: Make Shared Lib for this
    // TODO: Add Check is already logged in (maybe due to a network error the user was sent here)
    async function _importKeypair(): Promise<AsymmFullKeyPair | null> {
        const importKeypairFile: File | null = await uploadData(false) as File;
        if (importKeypairFile == null)
            return null;

        // TODO: Add Exporting User Keypair with a password + Importing a password encrypted Keypair (For safety reasons)
        const importKeypairObj = await readJsonFile<AsymmFullJsonKeypair>(importKeypairFile);
        if (importKeypairObj == null)
            return null;

        return parseFullKeypair(importKeypairObj);
    }

    async function importKeypair() {
        const keypair = await _importKeypair();
        const handle = keypair == null ? null : await deriveHandleFromPublicSplitKey(keypair.pub);
        setDerivedHandle(handle);

        if (keypair == null) {
            setUsernamePubKey("");
            setPasswordPrivKey("");
        } else {
            setUsernamePubKey(keypair.pub.serialize());
            setPasswordPrivKey(keypair.priv.serialize());
        }
    }

    async function updateUsernamePassword(username: string, password: string) {
        setUsernamePubKey(username);
        setPasswordPrivKey(password);

        if (username == "" || password == "") {
            setDerivedHandle(null);
            return;
        }

        try {
            const maybeKeypair = parseFullKeypair({
                pub: username,
                priv: password
            });
            const handle = await deriveHandleFromPublicSplitKey(maybeKeypair.pub);
            setDerivedHandle(handle);
        } catch {
            setDerivedHandle(null);
        }
    }

    async function login() {
        if (usernamePubKey == "" || passwordPrivKey == "") {
            alert("Please enter both username and password (or import a keypair)");
            return;
        }

        let resKeypair;
        try {
            resKeypair = parseFullKeypair({
                pub: usernamePubKey,
                priv: passwordPrivKey
            });
        } catch {
            const res = await loadLogin(usernamePubKey, passwordPrivKey);
            if (res == null) {
                alert("Incorrect Username or Password (or not setup)");
                return;
            }
            resKeypair = res;
        }

        await saveKeypair(resKeypair);

        if (!(await isUser())) {
            await saveKeypair(null);
            alert("Login failed: Not a user");
            return;
        }

        // Check to store in LocalStorage, because I keep forgetting when I delete my cache / test on diff devices
        if ((await getStorageMode() == "SESSION_STORAGE"))
            if (confirm("Do you want to store your keypair in localStorage?"))
                await setStorageMode("LOCAL_STORAGE");

        // Redirect to specified page or go to Home page
        const queryParams = new URLSearchParams(window.location.search);
        const redirect = queryParams.get("redirectAfter");
        if (redirect != null)
            window.location.href = redirect;
        else
            goPath("/user/home");
    }

    // TODO: Styling
    return (
        <main>
            <div className={styles.MainCont}>
                <h2 className={styles.Title}>Login</h2>

                <br/>
                <p>Please Login using your Identity Keypair.</p>

                <br/><hr/><br/>
                <label>Public Key</label><br/>
                <input type={"username"} ref={usernameRef} autoComplete="username" placeholder={"Public Key"} value={usernamePubKey} onChange={(e) => {
                    updateUsernamePassword(e.target.value, passwordPrivKey).then();
                }}></input> {derivedHandle == null ? null : <span>(Handle{":"} <b>{derivedHandle}</b>)</span>}<br/><br/>
                <label>Private Key</label><br/>
                <input type={"password"} ref={passwordRef} autoComplete="password" placeholder={"Private Key"} value={passwordPrivKey} onChange={(e) => {
                    updateUsernamePassword(usernamePubKey, e.target.value).then();
                }} onKeyDown={(e) => {
                    if (e.key == "Enter") {
                        e.preventDefault();
                        login().then();
                    }
                }}></input><br/><br/>


                <button onClick={importKeypair}>Import Keypair</button>
                <br/><hr/><br/>

                <button onClick={login}>Login</button><br/>

                <div className={styles.MainButtons}>
                    <Link href={"/guest/register"}>Register</Link>
                    <Link href={"/"}>Index</Link>
                </div>
            </div>
        </main>
    );
}
