'use client';

import styles from "./component.module.css";
import {
    deleteAllCachedStorage,
    getBaseServerUrl,
    getStorageMode,
    setBaseServerUrl,
    setStorageMode
} from "@/libs/auth-store";
import {useEffect, useState} from "react";

export default function Component() {
    const [storeLocal, setStoreLocal] = useState<boolean | null>(null);
    const [backendUrl, setBackendUrl] = useState<string | null>(null);

    useEffect(() => {
        (async () => {
            if (storeLocal === null) {
                const mode = await getStorageMode();
                setStoreLocal(mode == "LOCAL_STORAGE");
            }
            if (backendUrl === null) {
                setBackendUrl(await getBaseServerUrl());
            }
        })();
    })

    async function updateStorageSetting(local: boolean) {
        await setStorageMode(local ? "LOCAL_STORAGE" : "SESSION_STORAGE");
        setStoreLocal(local);
    }

    async function resetServerUrl() {
        setBackendUrl(await getBaseServerUrl());
    }

    async function saveServerUrl( ) {
        if (backendUrl === null || backendUrl == "")
            return;
        await setBaseServerUrl(backendUrl);
        alert("Server saved successfully.");
    }

    async function deleteAllCachedData() {
        if (!confirm("Are you sure?"))
            return;
        if (!confirm("Are you really sure?"))
            return;

        await deleteAllCachedStorage();
    }

    return (<div className={styles.DialogContent}>
        <h2 className={styles.Header}>Global Settings</h2>
        <p>
            Here are global Settings regarding storage and backend stuff.
        </p><br/>

        <br/>
        <label>Backend Server URL: </label><br/>
        <input type={"text"} value={backendUrl ?? ""} onChange={(e) => {
            setBackendUrl(e.target.value);
        }}></input> <button onClick={saveServerUrl}>Save</button> <button onClick={resetServerUrl}>Reset</button><br/>

        <br/>
        <label>Do you want to store your keypair in localStorage? </label>
        <input type={"checkbox"} checked={storeLocal ?? false} onChange={(e) => {
            updateStorageSetting(e.target.checked).then();
        }}></input><br/>

        <br/><br/>
        <button onClick={deleteAllCachedData}>Delete All Cached Data</button><br/>
    </div>);
}