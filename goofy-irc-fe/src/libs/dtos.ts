'use client';

import {WsServerManager} from "@/libs/ws";

export interface IrcExceptionDto {
    errorCode: number;
    message: string;
    details: Map<string, object> | null;
}

export class RequestError extends Error {
    httpCode: number;
    message: string;

    constructor(httpCode: number, error: string) {
        super();
        this.httpCode = httpCode;
        this.message = error;
    }

    toString(): string {
        return `${this.httpCode} -> ${this.message}`;
    }
}

export class RequestIrcError extends Error {
    httpCode: number;
    errorCode: number;
    message: string;
    details: Map<string, object> | null;

    constructor(httpCode: number, error: IrcExceptionDto) {
        super();
        this.httpCode = httpCode;
        this.errorCode = error.errorCode;
        this.message = error.message;
        this.details = error.details;
    }

    toString(): string {
        return `${this.httpCode} ${this.errorCode} -> ${this.message} (${JSON.stringify(this.details)})`;
    }
}

// 1_XXX_YYY
export class AllClientErrorCodes {
    static readonly INVALID_SIGNATURE: number = 1_001_001;
    // TODO: Use?
}

// 2_XXX_YYY
export class AllServerErrorCodes {
    static readonly DEFAULT: number = 2_000_000;
    static readonly PUBLIC_KEY_LOOKUP_FAILED: number = 2_001_001;
}



export interface GeneralInfoDto {
    frontendUrl: string;
    url: string;
    name: string;
    description: string;
    version: string;
    pubKey: string;
    handle: string;
    supportedAsymmCryptoTypes: string[];
    supportedSymmCryptoTypes: string[];
}

export interface RegisterStatusDto {
    registrationsAllowed: boolean;
    checkMethod: string;
    autoAllowDomains: string[];
}

export type AuthRole = "OUTSIDE_ENTITY" | "REGISTERED_USER" | "ADMIN";

export interface MyUserInfoDto {
    handle: string;
    handleDomain: string;
    pubKey: string;
    authRole: AuthRole;
    isRestricted: boolean;
    friendRequestTablePath: string;
    receivedDmsTablePath: string;
}

export interface RegistrationRequestDto {
    message: string;
    contact: string;
    optEmail?: string;
}

export interface MemInfoDto {
    memUsed: number;
    memMax: number;
    utilized: number;
}

export interface RegistrationCodeDto {
    code: string;
    admin: boolean;
    createdByHandle: string;
    createdAt: string;
    usedByHandle: string;
    usedAt: string;
}

// For Exporting/Importing
export interface ExportIdentityKeypair {
    pub: string;
    priv: string;
    handleFull: string;
}

export interface MemberStatusDto {
    isOnline?: boolean | null;
    typingInRoom?: string | null;
}

export interface ChatRoomDto {
    name: string; // pattern: ^[a-z0-9_]+$
    description: string; // size max: FieldSize.NORMAL_TEXT_LEN
    userLimit?: number | null;
    allowGuests: boolean;
    allowJoining: boolean;
    roomPasswordHash?: string | null;
    needsPassword?: boolean;
    memberCount?: number | null;
    members?: string[] | null;
    memberStatus?: MemberStatusDto[] | null;
    bannedUsers?: string[] | null;
    createdByHandle?: string | null;
    createdAt?: string | null; // ISO timestamp string (maps from Instant)
}



export class WsGenericEv {
    evType: WsGenericEvEventType;

    constructor(evType: WsGenericEvEventType) {
        this.evType = evType;
    }
}

export type WsGenericEvEventType =
    | "SEND_MSG"
    | "RECEIVE_MSG"
    | "UPDATE_TYPING"
    | "UPDATE_ROOM_LIST"
    | "UPDATE_ROOM_DATA"
    | "ERROR";

export class WsError extends WsGenericEv {
    errorMsg: string;

    constructor(errorMsg: string) {
        super("ERROR");
        this.errorMsg = errorMsg;
    }
}

export class WsReceiveMsg extends WsGenericEv {
    roomName: string;
    senderHandle: string;
    msgObj: string;
    sig: string;

    constructor(roomName: string, senderHandle: string, msgObj: string, sig: string) {
        super("RECEIVE_MSG");
        this.roomName = roomName;
        this.senderHandle = senderHandle;
        this.msgObj = msgObj;
        this.sig = sig;
    }
}

export class WsSendMsg extends WsGenericEv {
    roomName: string;
    msgObj: string;
    sig: string;

    constructor(roomName: string, msgObj: string, sig: string) {
        super("SEND_MSG");
        this.roomName = roomName;
        this.msgObj = msgObj;
        this.sig = sig;
    }
}

export interface ChatMessageDto {
    msg: string;
}

export class WsUpdateRoomData extends WsGenericEv {
    roomName: string;

    constructor(roomName: string) {
        super("UPDATE_ROOM_DATA");
        this.roomName = roomName;
    }
}

export class WsUpdateRoomList extends WsGenericEv {
    constructor() {
        super("UPDATE_ROOM_LIST");
    }
}

export class WsUpdateTyping extends WsGenericEv {
    roomName: string | null;

    constructor(roomName: string | null) {
        super("UPDATE_TYPING");
        this.roomName = roomName;
    }
}

export interface LocalServerData {
    serverUrl: string;
    serverName: string;
    ws: WsServerManager;
}

export interface LocalRoomData {
    room: ChatRoomDto;
    server: LocalServerData;
    sameRoomNameInDiffServer: boolean;
}



export interface LocalChatMessage {
    msgObj: ChatMessageDto;
    handle: string;
    timestamp: Date;
    uuid: string;
    sig: string;
    sigValid: boolean;
}


export interface IrcHandleLookupDto {
    handle: string;
    handleDomain: string;
    pubKey: string;
    registeredHere: boolean;
}