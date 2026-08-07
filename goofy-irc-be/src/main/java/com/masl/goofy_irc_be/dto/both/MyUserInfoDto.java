package com.masl.goofy_irc_be.dto.both;

import com.masl.goofy_irc_be.config.ROLES;
import com.masl.goofy_irc_be.entity.User;
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
    private String friendListTablePath;
    private User.FriendRequestSetting friendRequestSetting;
}
