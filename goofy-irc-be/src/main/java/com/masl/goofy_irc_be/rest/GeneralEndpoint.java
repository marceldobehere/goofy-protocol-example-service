package com.masl.goofy_irc_be.rest;

import com.masl.goofy_protocol_core.crypto.isolated.asymm.AsymmCryptoType;
import com.masl.goofy_protocol_core.crypto.isolated.symm.SymmCryptoType;
import com.masl.goofy_irc_be.auth.GoofyAuthUser;
import com.masl.goofy_irc_be.dto.request.GeneralReportDto;
import com.masl.goofy_irc_be.dto.response.GeneralInfoDto;
import com.masl.goofy_irc_be.exception.base.swagger.IrcEndpoint;
import com.masl.goofy_irc_be.properties.GeneralProperties;
import com.masl.goofy_irc_be.service.GeneralReportService;
import com.masl.goofy_irc_be.test_data.test_dev_prod.TestDataKeypair;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;

@RestController
@RequestMapping("/api/general")
@Tag(name = "General", description = "General Endpoints regarding the IRC")
public class GeneralEndpoint {
    private final GeneralProperties generalProperties;
    private final GeneralReportService generalReportService;
    private final TestDataKeypair testDataKeypair;

    public GeneralEndpoint(GeneralProperties generalProperties, GeneralReportService generalReportService, TestDataKeypair testDataKeypair) {
        this.generalProperties = generalProperties;
        this.generalReportService = generalReportService;
        this.testDataKeypair = testDataKeypair;
    }

    @GetMapping("/status")
    @IrcEndpoint(summary = "Get the Status of the IRC Backend. Mostly just `XYZ is running!`")
    public String status() {
        return "IRC Backend is running!";
    }

    @GetMapping("/info")
    @IrcEndpoint(summary = "Get General Information about the IRC Backend", description = "The information includes: the IRC Name, Description, Version, Public Split Key and the supported Asymmetric/Symmetric Crypto Types.")
    public GeneralInfoDto info() {
        return new GeneralInfoDto(
                generalProperties.getFrontendUrl(),
                generalProperties.getUrl(),
                generalProperties.getDomain(),
                generalProperties.getName(),
                generalProperties.getDescription(),
                GeneralProperties.version,
                testDataKeypair.getServerKeypair().pub().serialize(),
                testDataKeypair.getServerHandle(),
                Arrays.stream(AsymmCryptoType.values()).map(AsymmCryptoType::name).toList(),
                Arrays.stream(SymmCryptoType.values()).map(SymmCryptoType::name).toList()
        );
    }

    @GetMapping("/contact")
    @IrcEndpoint(summary = "Get Contact Information for the Instance Owner. Can be signed by the user")
    public String contact() {
        return generalProperties.getContact();
    }

    // TODO: Rate Limit
    @PostMapping("/report")
    @IrcEndpoint(summary = "Report a General Issue to the Instance Owner. Can be signed by the user")
    public void report(@Valid @RequestBody GeneralReportDto report, @AuthenticationPrincipal GoofyAuthUser auth) {
        String optHandle = auth != null ? auth.getHandle() : null;
        generalReportService.submitReport(report, optHandle);
    }
}
