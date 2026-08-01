package com.masl.goofy_irc_be.dto.response;

import com.masl.goofy_irc_be.entity.FieldSize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportEntryDto {
    private Long id;

    @NotBlank
    @Size(max = FieldSize.TITLE_LEN)
    private String title;

    @NotBlank
    @Size(max = FieldSize.NORMAL_TEXT_LEN)
    private String description;

    @NotBlank
    @Size(max = FieldSize.SHORT_TEXT_LEN)
    private String contact;

    private String optionalHandle;

    @NotNull
    private Instant createdAt;

    private Instant resolvedAt; // Also acts as boolean
}
