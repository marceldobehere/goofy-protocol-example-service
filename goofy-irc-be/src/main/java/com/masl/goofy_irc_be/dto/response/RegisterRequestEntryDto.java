package com.masl.goofy_irc_be.dto.response;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequestEntryDto {
    @NotNull
    private Long id;

    private String message;

    private String contact;

    private String optEmail;

    @NotNull
    private Instant createdAt;

    private Instant resolvedAt; // Also acts as boolean
}
