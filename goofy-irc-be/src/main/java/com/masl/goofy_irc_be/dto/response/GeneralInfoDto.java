package com.masl.goofy_irc_be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GeneralInfoDto {
    private String frontendUrl;
    private String url;
    private String domain;
    private String name;
    private String description;
    private String version;
    private String pubKey;
    private String handle;
    private List<String> supportedAsymmCryptoTypes;
    private List<String> supportedSymmCryptoTypes;
}
