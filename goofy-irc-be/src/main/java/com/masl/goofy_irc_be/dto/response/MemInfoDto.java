package com.masl.goofy_irc_be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MemInfoDto {
    private Long memUsed;
    private Long memMax;
    private Double utilized;
}
