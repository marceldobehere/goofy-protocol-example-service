'use client';

import {usePathname} from "next/navigation";
import {useEffect, useState} from "react";
import {getUserInfo} from "@/libs/auth";
import {MyUserInfoDto, ServiceEntryDto} from "@/libs/dtos";
import {goPath} from "@/libs/go-path";
import {getIdentityKeypair, getServiceEntry} from "@/libs/auth-store";
import {AsymmFullKeyPair} from "@/libs/crypto-types";

export const GlobalState: {
    loggedIn: boolean,
    isAdmin: boolean,
    handle: string | null,
    gotBaseData: boolean,

    // Identity Fragment
    identityKeypair: AsymmFullKeyPair | null,
    identityHandle: string | null

    // Service Entry
    serviceEntry: ServiceEntryDto | null
} = {
    loggedIn: false,
    isAdmin: false,
    handle: "",
    gotBaseData: false,

    // Identity Fragment
    identityKeypair: null,
    identityHandle: null,

    // Service Entry
    serviceEntry: null,
};

// eslint-disable-next-line @typescript-eslint/no-unsafe-function-type
async function runMaybeAsyncCallback(fn: Function | undefined): Promise<void> {
    if (fn === undefined)
        return;
    try {
        await fn();
    } catch (e) {
        console.info("> Error in callback: ", e);
    }
}

// What sort of fragment is needed
export type FragmentNeed = "NONE" | "IDENTITY" | "IDENTITY@SERVICE";

// eslint-disable-next-line @typescript-eslint/no-unsafe-function-type
export function useAsyncEffect(callback: Function, deps: unknown[]) {
    useEffect(() => {
        runMaybeAsyncCallback(callback).then();
    // eslint-disable-next-line
    }, deps);
}

export function isNetworkErrorTypeError(err: unknown): err is TypeError {
    return err instanceof TypeError && /NetworkError/i.test(err.message);
}

// eslint-disable-next-line @typescript-eslint/no-unsafe-function-type
export function useGlobalState(needLogin: boolean, needAdmin: boolean, fragment: FragmentNeed, doneCallback: Function, preCallback: Function | undefined = undefined, extraDependencies: never[] | undefined = undefined) {
    const pathName = usePathname();
    const [isDone, setIsDone] = useState(false);
    if (typeof window !== "undefined")
        console.debug("1> Use Global State", pathName, isDone, needLogin, needAdmin, fragment, extraDependencies);
    const deps = extraDependencies === undefined ? [pathName, isDone] : [pathName, isDone, ...extraDependencies];
    useEffect(() => {
        // SSR shouldn't do anything
        if (typeof window == "undefined")
            return;

        // console.log("2> Use Global State Effect")
        runMaybeAsyncCallback(preCallback).then(() => {
            if (isDone) {
                // We only have this here, so that the page is "refreshed" once the init is done and shows the GlobalState data correctly
                // console.log("5> Init Already Complete");
            } else {
                initGlobalState(pathName, needLogin, needAdmin, fragment).then(async (success) => {
                    // console.log("5> Init Complete");
                    if (success) {
                        runMaybeAsyncCallback(doneCallback).then(() => {
                            setIsDone(true);
                        });
                    }
                });
            }
        });

    // eslint-disable-next-line
    }, deps);
}

async function initGlobalState(pathName: string, needLogin: boolean, needAdmin: boolean, fragment: FragmentNeed): Promise<boolean> {
    // console.log("3> Init Global State", pathName, needLogin, needAdmin, fragment);

    // Get User State
    if (!GlobalState.gotBaseData) {
        let userInfo: MyUserInfoDto | null = null;
        try {
            userInfo = await getUserInfo(true);
        } catch (e) {
            if (isNetworkErrorTypeError(e)) {
                GlobalState.loggedIn = false;
                GlobalState.isAdmin = false;
                GlobalState.handle = null;
                GlobalState.gotBaseData = false;
                console.error("3> Init failed (Network Error)", e);
                alert("Network Error: Failed to get user info. Please check your network connection.");
                return false;
            }
        }
        GlobalState.loggedIn = userInfo != null && (userInfo.authRole == "REGISTERED_USER" || userInfo.authRole == "ADMIN");
        GlobalState.isAdmin = userInfo != null && userInfo.authRole == "ADMIN";
        GlobalState.handle =  userInfo != null ? userInfo.handle : null;
        GlobalState.gotBaseData = true;
        console.debug("3> Global State: ", GlobalState);
    }

    // Reset Specific Data
    GlobalState.identityKeypair = null;
    GlobalState.identityHandle = null;
    GlobalState.serviceEntry = null;

    // User Check
    if (needLogin && !GlobalState.loggedIn) {
        console.log("4> Init failed (Need Login)");
        goPath(`/guest/login?redirectAfter=${encodeURIComponent(window.location.href)}`);
        return false;
    }

    // Admin Check
    if (needAdmin && !GlobalState.isAdmin) {
        console.log("4> Init failed (Need Admin)");
        alert("Admin access required to view this page");
        goPath("/user/home");
        return false;
    }

    // Fragment Check
    if (fragment !== "NONE") {
        if (window.location.hash == "") {
            console.log("4> Init failed (Need Fragment)");
            goPath("/user/home");
            return false;
        }

        if (fragment == "IDENTITY") {
            const fragmentHandle =  window.location.hash.slice(window.location.hash.lastIndexOf("#") + 1);
            // window.location.hash = "#" + fragmentHandle;

            try {
                GlobalState.identityKeypair = await getIdentityKeypair(fragmentHandle);
                GlobalState.identityHandle = fragmentHandle;
            } catch (e) {
                if (isNetworkErrorTypeError(e)) {
                    console.error("4> Init failed (Network Error)", e);
                    alert("Network Error: Failed to get user info");
                } else {
                    console.log("4> Init failed (Identity Fragment Error)", e);
                    alert(`Identity for ${fragmentHandle} not found`);
                    goPath("/user/home");
                }
                return false;
            }
        } else if (fragment == "IDENTITY@SERVICE") {
            const fragmentPart =  window.location.hash.slice(window.location.hash.lastIndexOf("#") + 1).split("@");
            // window.location.hash = "#" + fragmentHandle;

            const fragmentHandle = fragmentPart[0];
            const fragmentUuid = fragmentPart[1];

            try {
                GlobalState.identityHandle = fragmentHandle;
                GlobalState.identityKeypair = await getIdentityKeypair(fragmentHandle);
                GlobalState.serviceEntry = await getServiceEntry(GlobalState.identityKeypair, fragmentUuid);
            } catch (e) {
                if (isNetworkErrorTypeError(e)) {
                    console.error("4> Init failed (Network Error)", e);
                    alert("Network Error: Failed to get user info");
                } else {
                    console.log("4> Init failed (Identity Fragment & Service Error)", e);
                    alert(`Identity for ${fragmentHandle} not found`);
                    goPath("/user/home");
                }
                return false;
            }
        } else {
            console.log("4> Init failed (Unknown Fragment Need)");
            goPath("/user/home");
            return false;
        }
    }

    console.debug("4> Init Success");
    return true;
}