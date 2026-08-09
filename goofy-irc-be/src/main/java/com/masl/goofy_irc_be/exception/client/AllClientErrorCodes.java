package com.masl.goofy_irc_be.exception.client;

// 1_XXX_YYY
public class AllClientErrorCodes {
    // public static final int BASE = 1_000_000;

    public static final int INVALID_SIGNATURE = 1_001_001;
    public static final int GENERIC_NOT_FOUND = 1_001_002;
    public static final int CONTENT_TOO_LARGE = 1_001_003;

    public static final int INVALID_REGISTER_CODE = 1_002_001;
    public static final int REGISTRATION_NOT_ALLOWED = 1_002_002;
    public static final int HANDLE_ALREADY_REGISTERED = 1_002_003;
    public static final int REGISTRATION_CODE_ALREADY_USED = 1_002_004;

    public static final int ROOM_ALREADY_EXISTS = 1_003_001;
    public static final int ROOM_CREATION_LIMIT_REACHED = 1_003_002;
    public static final int ROOM_INVALID = 1_003_003;
    public static final int ROOM_NOT_FOUND = 1_003_004;
    public static final int ROOM_ACTION_NOT_ALLOWED = 1_003_005;
    public static final int ROOM_INVALID_PASSWORD_HASH = 1_003_006;
    public static final int ROOM_JOINING_NOT_ALLOWED = 1_003_007;
    public static final int ROOM_MEMBER_LIMIT_REACHED = 1_003_008;
    public static final int ROOM_MEMBER_NOT_FOUND = 1_003_009;
    public static final int ROOM_MEMBER_ALREADY_BANNED = 1_003_010;
    public static final int ROOM_MEMBER_NOT_BANNED = 1_003_011;
    public static final int ROOM_MEMBER_IS_BANNED = 1_003_012;

    public static final int PRIV_USER_NOT_FOUND = 1_004_001;
    public static final int PRIV_USER_FIS_LOOKUP_FAILED = 1_004_002;
    public static final int PRIV_NOT_FRIENDS = 1_004_003;
    public static final int PRIV_FRIEND_REQUESTS_NOT_ALLOWED = 1_004_004;
    public static final int PRIV_FRIEND_REQUEST_ALREADY_SENT = 1_004_005;
    public static final int PRIV_ALREADY_FRIENDS = 1_004_006;
}
