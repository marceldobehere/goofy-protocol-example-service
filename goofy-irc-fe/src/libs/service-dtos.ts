'use client';

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

export interface LocalTableStructure {
    tableName: string;
    schemaVersion?: number;
    columns?: TableColumnDto[];
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

export interface TableUpdateDto {
    colNames: string[];
    colValues: never[];
    basicQuery?: TableBasicQueryDto;
}