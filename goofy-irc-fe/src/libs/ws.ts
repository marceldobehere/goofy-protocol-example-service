'use client';

import {GlobalState} from "@/libs/global-state";
import {getBaseServerUrl, getKeypair, hasKeypair} from "@/libs/auth-store";
import {sleep} from "@/libs/utils";
import {createSignedRequest, getHeadersFromSignedRequestWithHandle,} from "@/libs/crypto";
import {AsymmFullKeyPair} from "@/libs/crypto-types";

let _msgHandlers: ((data: never) => never)[] = [];
let _sock: WebSocket | null = null;

const WS_PATH = "/api/ws"
export async function getWsUrl(): Promise<string> {
    const baseUrl = await getBaseServerUrl(); // can be https://... or http://...
    if (baseUrl.startsWith("https://"))
        return baseUrl.replace("https://", "ws://") + WS_PATH;
    else if (baseUrl.startsWith("http://"))
        return baseUrl.replace("http://", "ws://") + WS_PATH;
    else
        throw new Error("Unknown base Server Url Protocol: " + baseUrl);
}

export async function createSignedRequestParams(keypair: AsymmFullKeyPair): Promise<string> {
    const req = await createSignedRequest(keypair, "GET", WS_PATH, null);
    const reqHeaders = getHeadersFromSignedRequestWithHandle(req);
    return reqHeaders.entries().map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`).toArray().join("&");
}

// Only call after Global State is initialized
export async function initWs(clearHandlers: boolean = true) {
    if (typeof window == "undefined")
        return;

    if (!GlobalState.gotBaseData || !GlobalState.loggedIn || !(await hasKeypair()))
        throw new Error("[WS INIT] Not logged in or base data not loaded");

    // Reset Handlers, as they will probably be added again, since the init has been called
    if (clearHandlers) {
        console.debug("[WS INIT] Starting Init")
        _msgHandlers = [];
    }

    // Handle already existing sockets
    if (_sock != null) {
        if (clearHandlers)
            console.debug("[WS INIT] Socket exists, in state: " + _sock.readyState);
        if (_sock.readyState == WebSocket.OPEN) {
            if (clearHandlers)
                console.debug("[WS INIT] WebSocket already open, skipping");
            return;
        } else if (_sock.readyState == WebSocket.CONNECTING) {
            if (clearHandlers)
                console.debug("[WS INIT] WebSocket already connecting, waiting for it to open");
            while (_sock.readyState == WebSocket.CONNECTING)
                await sleep(100);
            await initWs(clearHandlers);
            return;
        } else {
            console.debug("[WS INIT] WebSocket already closed/closing");
            try {
                _sock.close();
            } catch {

            }
            _sock = null;
        }
    }

    // Create new Socket
    const wsUrl = await getWsUrl() + "?" + await createSignedRequestParams(await getKeypair());
    _sock = new WebSocket(wsUrl);

    // Attach Internal Handlers
    _sock.onopen = _wsOpened;
    _sock.onmessage = _wsMsg;
    _sock.onerror = _wsError;
    _sock.onclose = _wsClosed;

    if (clearHandlers)
        console.debug("[WS INIT] Init Done");
}

// Internal Handlers
function _wsOpened() {
    console.debug("[WS] WebSocket opened");
}

function _wsError(e: Event) {
    console.error("[WS] WebSocket error", e);
    alert("WebSocket error occurred!");
}

function _wsClosed() {
    console.debug("[WS] WebSocket closed");

    // Attempt to reconnect after 3s
    setTimeout(async () => {
        await initWs(false);
    }, 3000);
}

function _wsMsg(event: MessageEvent) {
    console.debug("[WS] WebSocket message", event.data, event);
    for (const handler of _msgHandlers) {
        try {
            handler(event.data as never);
        } catch {

        }
    }
}


// External Handlers
export async function sendRawWsMessage(msg: string) {
    console.debug("[WS] sendRawWsMessage", msg);
    await initWs(false);
    _sock!.send(msg);
}

export async function attachRawWsMessageHandler(handler: (msg: string) => void) {
    _msgHandlers.push(handler as never);
}