'use client';

import styles from "./page.module.css";
import Link from "next/link";
import {ExportIdentityKeypair, RegisterStatusDto, RegistrationRequestDto} from "@/libs/dtos";
import {getStorageMode, saveKeypair, setStorageMode} from "@/libs/auth-store";
import {useState} from "react";
import {doRegistration, isRegisterCodeValid, sendRegistrationRequest} from "@/libs/register";
import {goPath} from "@/libs/go-path";
import {useGlobalState} from "@/libs/global-state";
import {importIdentityKeypair, parseIdentityKeypair} from "@/libs/auth";
import {getNoAuth} from "@/libs/req";

export default function Page() {
    const [importedKeypair, setImportedKeypair] = useState<ExportIdentityKeypair | null>(null);
    const [regCodeNeeded, setRegCodeNeeded] = useState<boolean>(true);
    const [registerCode, setRegisterCode] = useState<string>("");
    const [errorText, setErrorText] = useState<string | null>(null);

    useGlobalState(false, false, "NONE", async () => {
    });

    async function checkRegisterCodeNeeded(kp: ExportIdentityKeypair) {
        const res: RegisterStatusDto = await getNoAuth("/api/register/status");
        console.log(res);
        const domainPart =  kp.handleFull.split("@")[1];
        setRegCodeNeeded(!res.autoAllowDomains.includes(domainPart));
    }

    async function importKeypair() {
        const imported = await importIdentityKeypair();

        if (imported != null) {
            try {
                await parseIdentityKeypair(imported);
                setImportedKeypair(imported);
                await checkRegisterCodeNeeded(imported);
                setErrorText(null);
            } catch (err: unknown) {
                console.error(err);
                setErrorText((err as Error).message);
            }
        } else {
            setImportedKeypair(imported);
            setRegCodeNeeded(true);
            setErrorText("You need to import your identity keypair");
        }
    }

    async function requestRegisterCode() {
        if (importedKeypair == null) {
            alert("You need to import your identity keypair first!");
            return;
        }

        const currKeypair = await parseIdentityKeypair(importedKeypair);

        const message = prompt("Please enter a message to be sent to the server for requesting a Register Code.");
        if (message == null || message == "") {
            return;
        }
        const contact = prompt("Please enter a contact method for follow up on the request.");
        if (contact == null || contact == "") {
            return;
        }

        const req: RegistrationRequestDto = {
            message,
            contact
        };

        const err = await sendRegistrationRequest(req, currKeypair);
        if (err != null)
            alert("Error requesting Register Code: " + err);
        else
            alert("Request sent! Please wait for a response from the server.");
    }

    async function checkRegisterCode(code: string): Promise<boolean> {
        if (code == "")
            return !regCodeNeeded;
        return await isRegisterCodeValid(code);
    }

    async function register() {
        if (importedKeypair == null) {
            alert("You need to import your identity keypair first!");
            return;
        }

        const currKeypair = await parseIdentityKeypair(importedKeypair);

        const codeValid: boolean = await checkRegisterCode(registerCode);
        if (!codeValid) {
            setErrorText("The Register Code is invalid!");
            return;
        }

        const domain = importedKeypair.handleFull.split("@")[1];
        const regErr = await doRegistration(registerCode, currKeypair, domain);
        if (regErr != null) {
            setErrorText("Registration failed: " + regErr);
            return;
        }

        alert("Registration successful!");
        await saveKeypair(currKeypair);

        // Check to store in LocalStorage, because I keep forgetting when I delete my cache / test on diff devices
        if ((await getStorageMode() == "SESSION_STORAGE"))
            if (confirm("Do you want to store your keypair in localStorage?"))
                await setStorageMode("LOCAL_STORAGE");

        goPath("/user/home");
    }

    return (
        <main>
            <div className={styles.MainCont}>
                <h2 className={styles.Title}>Register</h2>

                <br/>
                <p>
                    Please import your identity keypair to be used as your identity and enter a valid register code.<br/><br/>
                    If your handle is from a supported domain, it may not require a register code!<br/>
                    If you don&apos;t have a register code, you can request one by pressing the button below.
                </p>

                <br/><hr/><br/>
                <label>Identity</label><br/>
                <button onClick={importKeypair}>Import Keypair</button><span> &nbsp; </span>
                <span>{importedKeypair == null ? "" : ` -> ${importedKeypair.handleFull}`}</span>
                <br/><br/>

                <label>Enter Register Code: ({regCodeNeeded ? "required" : "optional"})</label><br/>
                <input type={"text"} placeholder={"Register Code"} value={registerCode} onChange={(e) => {
                    setRegisterCode(e.target.value);
                }}></input><br/>

                <br/>{errorText != null ? (<span className={styles.ErrorText}>{errorText}</span>) : null}

                <br/><hr/><br/>
                <button onClick={register} disabled={importedKeypair == null}>Register</button><br/>
                <button onClick={requestRegisterCode} disabled={importedKeypair == null} title={"Need to import a keypair to be able to request a register code!"}>Request a Register Code</button><br/>

                <div className={styles.MainButtons}>
                    <Link href={"/guest/register"}>Register</Link>
                    <Link href={"/"}>Index</Link>
                </div>
            </div>
        </main>
    );
}
