'use client';

import styles from "./page.module.css";
import Link from "next/link";
import {GlobalState, useGlobalState} from "@/libs/global-state";
import {MemInfoDto, RegistrationCodeDto} from "@/libs/dtos";
import {getAuth, postAuth} from "@/libs/req";
import {useState} from "react";

export default function Page() {
    const [registerCodes, setRegisterCodes] = useState<RegistrationCodeDto[]>([]);

    useGlobalState(true, true, "NONE", async () => {
        await refresh();
    });

    async function refresh() {
        const unused: RegistrationCodeDto[] = await getAuth("/api/admin/register/code/unused");
        const used: RegistrationCodeDto[] = await getAuth("/api/admin/register/code/used");
        setRegisterCodes([...unused, ...used]);
    }

    async function getMemInfo() {
        const res: MemInfoDto = await getAuth("/api/admin/general/memory");
        const info = {
            totalMb: res.memMax / (1024 * 1024),
            usedMb: res.memUsed / (1024 * 1024),
            utilized: res.utilized
        };
        alert("Mem Info: " + JSON.stringify(info, null, 2));
    }

    async function forceGc() {
        await postAuth("/api/admin/general/memory/gc", {});
    }

    async function createRegisterCode() {
        const opt = prompt("Should the register code be for a `user` or `admin`?", "user");
        if (opt == "" || opt == null)
            return;

        if (opt != "user" && opt != "admin") {
            alert("Invalid option. Must be `user` or `admin`.");
            return;
        }

        const res: RegistrationCodeDto = await postAuth("/api/admin/register/code", (opt == "admin") as unknown as object);
        console.log(res);
        alert("Registered: " +JSON.stringify(res, null, 2));
        await refresh();
    }

    // TODO: Styling
    return (
        <main>
            <div className={styles.MainCont}>
                <h2 className={styles.Title}>Admin Home</h2>

                <br/>
                <p>Hello, {GlobalState.handle}! This is the Admin Home Page.</p>

                <br/><hr/><br/>

                <button onClick={getMemInfo}>Get Memory Info</button><br/>
                <button onClick={forceGc}>Force GC</button><br/>

                <br/><hr/><br/>

                <h3>Registration Codes</h3>

                <button onClick={createRegisterCode}>Create Register Code</button><br/>

                <br/>
                {registerCodes.length == 0 ? <p>No registration codes found.</p> : (
                    <ul>
                        {registerCodes.map((code) => (<li key={code.code}>
                            <span>
                                Code: {code.code} - Type: {code.admin ? "Admin" : "User"} - Created: {new Date(code.createdAt).toLocaleString()} <br/>
                                Used At: {code.usedAt ? new Date(code.usedAt).toLocaleString() : "N/A"} - Used By: {code.usedByHandle}  <br/>
                                Actions: ... ...<br/>
                                &nbsp;
                            </span>
                        </li>))}
                    </ul>
                )}

                <br/><hr/><br/>


                <div className={styles.MainButtons}>
                    <Link href={"/user/home"}>Home</Link>
                    <Link href={"/"}>Index</Link>
                </div>
            </div>
        </main>
    );
}
