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
    static readonly DEFAULT: number = 1_000_000;
    static readonly INVALID_SIGNATURE: number = 1_001_001;
    static readonly INVALID_REGISTER_CODE: number = 1_002_001;
    static readonly REGISTRATION_NOT_ALLOWED: number = 1_002_002;
    static readonly HANDLE_ALREADY_REGISTERED: number = 1_002_003;
    static readonly REGISTRATION_CODE_ALREADY_USED: number = 1_002_004;
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

export interface IdentityStorageEntryDto {
    handle: string;
    name: string;
    pubSplitKey: string;
    encKeypairEntry: string;
    encKeypairEntrySignature: string;
}

export interface MyIdentityEntryQuotasDto {
    maxEntryCount: number;
    currentEntryCount: number;
}

export interface ServiceEntryDto {
    name: string;
    uuid: string;
    usedService?: string;
}

export interface MyServiceEntryQuotasDto {
    maxServiceEntryCount: number;
    currentServiceEntryCount: number;
}

export type CacheDuration = "NONE" | "SHORT" | "NORMAL" | "LONG" | "VERY_LONG";

export interface ServiceBucketEntryDto {
    fileUuid: string;
    contentType: string;
    filename: string;
    cacheDuration: CacheDuration;
    handlesWithReadPerms: string[];
    handlesWithWritePerms: string[];

    contentSize?: number;
    createdAt?: string; // Format: "2026-07-12T00:40:21.272978Z"
    createdAtDate?: Date;
}

export interface ServiceBucketPermissionDto {
    handlesWithReadPerms: string[];
    handlesWithWritePerms: string[];
}

export interface ServiceBucketQuotasDto {
    maxBucketSize: number;
    maxItemSize: number;
    maxItemCount: number;
    maxUniquePermissionCount: number;

    currentBucketSize: number;
    currentItemCount: number;
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

export interface ServiceTableQuotasDto {
    currTableCount: number;
    currColumnCount: number;
    currRowCount: number;

    maxTableCount: number;
    maxColumnCount: number;
    maxRowCount: number;

    maxFieldSize: number;
    maxUniquePermissionCount: number;
}

export interface ServiceDbQuotasDto {
    currTableCount: number;
    currDbSize: number;

    maxTableCount: number;
    maxDbSize: number;

    maxFieldSize: number;
    maxColumnCount: number;
    maxRowCount: number;

    maxUniquePermissionCount: number;
    maxLockDurationSeconds: number;

    maxQueryLength: number;
    maxConditionCount: number;
    maxResultCount: number;
    generalMaxNameSize: number;
}

export type ColType = "FIXED_STRING_N" | "VAR_STRING_N" | "BOOLEAN" | "TINYINT" | "SMALLINT" | "INT" | "BIGINT" | "FLOAT" | "DOUBLE" | "DATE" | "TIME";

export type ColConstraint = "NOT_NULL" | "UNIQUE" | "PRIMARY_KEY";

export interface TableColumnDto {
    colName: string;
    type: ColType;
    typeSize?: number;
    constraints: ColConstraint[];
    defaultValue?: unknown;
}

export interface ServiceTableEntryDto {
    tableUuid?: string;
    tableName: string;

    schemaVersion?: number;
    columns?: TableColumnDto[];

    createdAt?: string;

    handlesWithReadPerms: string[];
    handlesWithWritePerms: string[];
}

export interface ServiceTableQueryResultDto {
    colNames: string[];
    colTypes: ColType[];
    rows: unknown[][];
    resultTruncated: boolean; // Was the result truncated by any sort of limit (user defined or by the quota)
}

export type TableWhereConditionType = "VAL" | "COL" |
    "M_ADD" | "M_SUB" | "M_MUL" | "M_DIV" | "M_MOD" | "M_FLOOR" | "M_CEIL" | "M_ABS" |
    "L_AND" | "L_OR" | "L_NOT" | "C_EQ" | "C_NEQ" | "C_GT" | "C_GE" | "C_LT" | "C_LE" |
    "COALESCE" | "LIKE";

export interface TableWhereConditionPart {
    type: TableWhereConditionType;

    // RAW VALUE
    value?: unknown;
    valueType?: ColType;

    // COLUMN
    colName?: string;

    // CONDITION
    conditionParts?: TableWhereConditionPart[];
}

export type SortOrder = "ASC" | "DESC";

export interface TableBasicQueryDto {
    // Where Statement
    where?: TableWhereConditionPart;

    // Sort By
    sortByCols?: string[];
    sortOrders?: SortOrder[];

    // Optional Limit
    limit?: number;

    // Optional Offset
    offset?: number | null;
}

export interface TableSelectDto {
    colNames: string[];
    basicQuery?: TableBasicQueryDto;
}

// For Exporting/Importing
export interface ExportIdentityKeypair {
    pub: string;
    priv: string;
    handleFull: string;
}