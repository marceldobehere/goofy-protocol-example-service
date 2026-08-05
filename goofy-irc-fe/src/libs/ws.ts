'use client';

import {GlobalState} from "@/libs/global-state";
import {getBaseServerUrl, getKeypair, hasKeypair} from "@/libs/auth-store";
import {sleep} from "@/libs/utils";
import {createSignedRequest, getHeadersFromSignedRequestWithHandle,} from "@/libs/crypto";
import {AsymmFullKeyPair} from "@/libs/crypto-types";
import {getAuth} from "@/libs/req";
import {IrcHandleLookupDto} from "@/libs/dtos";

const WS_PATH = "/api/ws";
const MANAGER_MAP = new Map<string, WsServerManager>();

export async function clearWsHandlers() {
    for (const manager of MANAGER_MAP.values())
        await manager.clearWsHandlers();
}

export async function createServerManager(serverUrl: string): Promise<WsServerManager> {
    if (!MANAGER_MAP.has(serverUrl)) {
        MANAGER_MAP.set(serverUrl, new WsServerManager(serverUrl, MANAGER_MAP));

        try {
            const res: IrcHandleLookupDto = await getAuth(`${serverUrl}/api/user/lookup/${GlobalState.handle}`);
            console.log(res);
        } catch (e) {
            console.error(e);
            alert(`Failed to lookup handle on server ${serverUrl}. This may indicate that the server is not reachable or that your handle is not known/allowed on this server. Error: ${e}`);
            throw e;
        }
    }

    const instance = MANAGER_MAP.get(serverUrl)!;
    await instance.initWs();
    return instance;
}

export class WsServerManager {
    #msgHandlers: ((data: never) => never)[] = [];
    #_sock: WebSocket | null = null;
    readonly serverUrl: string;

    constructor(serverUrl: string, ref: Map<string, WsServerManager>) {
        this.serverUrl = serverUrl;
        if (ref != MANAGER_MAP)
            throw new Error("ref must be provided");
    }

    async #getWsUrl(): Promise<string> {
        const baseUrl = this.serverUrl || await getBaseServerUrl(); // can be https://... or http://...
        if (baseUrl.startsWith("https://"))
            return baseUrl.replace("https://", "wss://") + WS_PATH;
        else if (baseUrl.startsWith("http://"))
            return baseUrl.replace("http://", "ws://") + WS_PATH;
        else
            throw new Error("Unknown base Server Url Protocol: " + baseUrl);
    }

    async #createSignedRequestParams(keypair: AsymmFullKeyPair): Promise<string> {
        const req = await createSignedRequest(keypair, "GET", WS_PATH, null);
        const reqHeaders = getHeadersFromSignedRequestWithHandle(req);
        return reqHeaders.entries().map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`).toArray().join("&");
    }

    async clearWsHandlers() {
        console.debug(`[WS INIT] Clearing current handlers (${this.#msgHandlers.length})`);
        this.#msgHandlers = [];
    }

    // Only call after Global State is initialized
    async initWs(clearHandlers: boolean = true) {
        if (typeof window == "undefined")
            return;

        if (!GlobalState.gotBaseData || !GlobalState.loggedIn || !(await hasKeypair()))
            throw new Error("[WS INIT] Not logged in or base data not loaded");

        // Reset Handlers, as they will probably be added again, since the init has been called
        if (clearHandlers) {
            console.debug("[WS INIT] Starting Init")
            this.#msgHandlers = [];
        }

        // Handle already existing sockets
        if (this.#_sock != null) {
            if (clearHandlers)
                console.debug("[WS INIT] Socket exists, in state: " + this.#_sock.readyState);
            if (this.#_sock.readyState == WebSocket.OPEN) {
                if (clearHandlers)
                    console.debug("[WS INIT] WebSocket already open, skipping");
                return;
            } else if (this.#_sock.readyState == WebSocket.CONNECTING) {
                if (clearHandlers)
                    console.debug("[WS INIT] WebSocket already connecting, waiting for it to open");
                while (this.#_sock.readyState == WebSocket.CONNECTING)
                    await sleep(100);
                await this.initWs(clearHandlers);
                return;
            } else {
                console.debug("[WS INIT] WebSocket already closed/closing");
                try {
                    this.#_sock.close();
                } catch {

                }
                this.#_sock = null;
            }
        }

        // Create new Socket
        const wsUrl = await this.#getWsUrl() + "?" + await this.#createSignedRequestParams(await getKeypair());
        this.#_sock = new WebSocket(wsUrl);

        // Attach Internal Handlers
        this.#_sock.onopen = this.#_wsOpened.bind(this);
        this.#_sock.onmessage = this.#_wsMsg.bind(this);
        this.#_sock.onerror = this.#_wsError.bind(this);
        this.#_sock.onclose = this.#_wsClosed.bind(this);

        if (clearHandlers)
            console.debug("[WS INIT] Init Done");
    }

    // Internal Handlers
    #_wsOpened() {
        console.debug("[WS] WebSocket opened");
    }

    #_wsError(e: Event) {
        console.error("[WS] WebSocket error", e);
        alert("WebSocket error occurred!");
    }

    #_wsClosed() {
        console.debug("[WS] WebSocket closed");

        // Attempt to reconnect after 3s
        setTimeout(async () => {
            await this.initWs(false);
        }, 3000);
    }

    #_wsMsg(event: MessageEvent) {
        // console.debug("[WS] WebSocket message", event.data, event);
        for (const handler of this.#msgHandlers) {
            try {
                handler(event.data as never);
            } catch {

            }
        }
    }


    // External Handlers
    async sendRawWsMessage(msg: string) {
        // console.debug("[WS] sendRawWsMessage", msg);
        await this.initWs(false);
        this.#_sock!.send(msg);
    }

    async attachRawWsMessageHandler(handler: (msg: string) => void) {
        this.#msgHandlers.push(handler as never);
    }
}