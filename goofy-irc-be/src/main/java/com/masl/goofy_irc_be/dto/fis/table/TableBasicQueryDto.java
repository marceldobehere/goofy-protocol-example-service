package com.masl.goofy_irc_be.dto.fis.table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TableBasicQueryDto {
    // Where Statement
    private TWherePart where;

    // Sort By
    private String[] sortByCols;
    private SortOrder[] sortOrders;

    // Optional Limit
    private Integer limit;

    // Optional Offset
    private Integer offset;

    public enum SortOrder {
        ASC,
        DESC
    }
}
