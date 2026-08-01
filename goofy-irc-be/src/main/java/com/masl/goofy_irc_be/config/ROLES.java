package com.masl.goofy_irc_be.config;

public class ROLES {
    // Outsider which has a valid signature (Can be a User or Service)
    public static final String OUTSIDE_ENTITY = "OUTSIDE_ENTITY";

    // Registered User
    public static final String REGISTERED_USER = "REGISTERED_USER";

    // Administrator
    public static final String ADMIN = "ADMIN";

    public enum AuthRoleEnumDto {
        OUTSIDE_ENTITY,
        REGISTERED_USER,
        ADMIN,
    }
}
