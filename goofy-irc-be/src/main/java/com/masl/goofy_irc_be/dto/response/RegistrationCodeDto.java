package com.masl.goofy_irc_be.dto.response;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegistrationCodeDto {
    @NotNull
    private String code;

    @NotNull
    private Boolean admin;

    private String createdByHandle;

    @NotNull
    private Instant createdAt;

    private String usedByHandle;

    private Instant usedAt;
}
