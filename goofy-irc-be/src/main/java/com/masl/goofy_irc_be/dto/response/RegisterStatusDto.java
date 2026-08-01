package com.masl.goofy_irc_be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterStatusDto {
    private Boolean registrationsAllowed;
    private String checkMethod;
}
