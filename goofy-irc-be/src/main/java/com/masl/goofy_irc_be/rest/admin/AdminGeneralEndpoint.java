package com.masl.goofy_irc_be.rest.admin;

import com.masl.goofy_irc_be.dto.response.MemInfoDto;
import com.masl.goofy_irc_be.dto.response.ReportEntryDto;
import com.masl.goofy_irc_be.exception.base.swagger.IrcEndpoint;
import com.masl.goofy_irc_be.exception.client.GenericNotFound;
import com.masl.goofy_irc_be.service.GeneralReportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.List;

// TODO: Test
@RestController
@RequestMapping("/api/admin/general")
@Tag(name = "General (Admin)", description = "General Admin Endpoints regarding the IRC Server")
public class AdminGeneralEndpoint {
    private static final Logger log = LoggerFactory.getLogger(AdminGeneralEndpoint.class);
    private final GeneralReportService generalReportService;

    public AdminGeneralEndpoint(GeneralReportService generalReportService) {
        this.generalReportService = generalReportService;
    }

    // Get Memory Health
    @GetMapping("/memory")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @IrcEndpoint(summary = "Get Memory Health", description = "This Endpoint allows an admin to retrieve the current memory usage of the IRC Server. <br> The response will include the amount of memory used, the maximum available memory, and the utilization percentage.")
    public MemInfoDto getMemInfo() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        double utilization = (double) heapUsage.getUsed() / heapUsage.getMax();
        log.info("Memory Usage: Used = {} MB, Max = {} MB, Utilization = {}%", heapUsage.getUsed() / (1024*1024), heapUsage.getMax() / (1024*1024), utilization * 100);
        return new MemInfoDto(heapUsage.getUsed(), heapUsage.getMax(), utilization);
    }

    // Force Garbage Collector
    @PostMapping("/memory/gc")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @IrcEndpoint(summary = "Force Garbage Collection")
    public void forceGc() {
        log.info("Forcing Garbage Collection");
        System.gc();
    }

    // Get all reports
    @GetMapping("/report")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @IrcEndpoint(summary = "Get All General Reports", description = "This Endpoint allows an admin to retrieve all general reports submitted to the IRC Server. <br> The response will be a list of report entries, each containing details such as ID, title, description, contact information, optional handle, creation timestamp, and resolution timestamp.")
    public List<ReportEntryDto> getAllReports() {
        return generalReportService.getAllReports().stream()
                .map(report -> new ReportEntryDto(
                        report.getId(),
                        report.getTitle(),
                        report.getDescription(),
                        report.getContact(),
                        report.getOptionalHandle(),
                        report.getCreatedAt(),
                        report.getResolvedAt()
                )).toList();
    }

    // Get all unresolved reports
    @GetMapping("/report/open")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @IrcEndpoint(summary = "Get All Unresolved General Reports", description = "This Endpoint allows an admin to retrieve all unresolved general reports submitted to the IRC Server. <br> The response will be a list of report entries that have not been marked as resolved, each containing details such as ID, title, description, contact information, optional handle, creation timestamp, and resolution timestamp (which will be null for unresolved reports).")
    public List<ReportEntryDto> getAllUnresolvedReports() {
        return generalReportService.getAllUnresolvedReports().stream()
                .map(report -> new ReportEntryDto(
                        report.getId(),
                        report.getTitle(),
                        report.getDescription(),
                        report.getContact(),
                        report.getOptionalHandle(),
                        report.getCreatedAt(),
                        report.getResolvedAt()
                )).toList();
    }

    // Get Report by id
    @GetMapping("/report/{id}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @IrcEndpoint(summary = "Get a General Report by ID", description = "This Endpoint allows an admin to retrieve a specific general report by its ID. <br> The response will contain details of the report, including ID, title, description, contact information, optional handle, creation timestamp, and resolution timestamp.")
    public ReportEntryDto getReportById(@PathVariable Long id) throws GenericNotFound {
        var report = generalReportService.getReportById(id);
        return new ReportEntryDto(
                report.getId(),
                report.getTitle(),
                report.getDescription(),
                report.getContact(),
                report.getOptionalHandle(),
                report.getCreatedAt(),
                report.getResolvedAt()
        );
    }

    // Resolve Report
    @PostMapping("/report/{id}/resolve")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @IrcEndpoint(summary = "Resolve a General Report by ID", description = "This Endpoint allows an admin to mark a general report as resolved or unresolved by its ID. <br> The request body should contain a boolean value indicating the resolved status. <br> Example: `true` for resolved, `false` for unresolved.")
    public void resolveReport(@PathVariable Long id, @Valid @RequestBody Boolean resolved) throws GenericNotFound {
        generalReportService.setResolvedStatus(id, resolved);
    }

    // Delete Report
    @DeleteMapping("/report/{id}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @IrcEndpoint(summary = "Delete a General Report by ID", description = "This Endpoint allows an admin to delete a specific general report by its ID.")
    public void deleteReport(@PathVariable Long id) {
        generalReportService.deleteReport(id);
    }
}
