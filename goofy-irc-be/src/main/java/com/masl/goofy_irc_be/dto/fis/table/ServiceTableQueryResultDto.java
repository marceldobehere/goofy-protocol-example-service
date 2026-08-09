package com.masl.goofy_irc_be.dto.fis.table;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ServiceTableQueryResultDto {
    @NotNull
    private String[] colNames;
    @NotNull
    private TableColumnDto.Type[] colTypes;
    @NotNull
    private Object[][] rows;
    @NotNull
    private Boolean resultTruncated; // Was the result truncated by any sort of limit (user defined or by the quota)
}
