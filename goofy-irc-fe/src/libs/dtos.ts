'use client';

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