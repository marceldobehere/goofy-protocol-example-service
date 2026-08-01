package com.masl.goofy_irc_be.dto.both;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {
    private String handle;
    private String pubKey;

    @NotNull
    private Boolean isAdmin;
    @NotNull
    private Boolean isRestricted;
}
