package com.masl.goofy_irc_be.dto.response;

import com.masl.goofy_irc_be.config.ROLES;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MyUserInfoDto {
    private String handle;
    private String handleDomain;
    private String pubKey;
    private ROLES.AuthRoleEnumDto authRole;
    private String friendRequestTablePath;
    private String receivedDmsTablePath;
}
