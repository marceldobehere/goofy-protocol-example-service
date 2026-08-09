package com.masl.goofy_irc_be.dto.fis.table;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TableSelectDto {
    @NotNull
    private String[] colNames;

    private TableBasicQueryDto basicQuery;
}
