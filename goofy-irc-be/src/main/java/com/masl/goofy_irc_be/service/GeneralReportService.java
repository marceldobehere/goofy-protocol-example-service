package com.masl.goofy_irc_be.service;

import com.masl.goofy_irc_be.dto.request.GeneralReportDto;
import com.masl.goofy_irc_be.entity.GeneralReport;
import com.masl.goofy_irc_be.exception.client.GenericNotFound;
import com.masl.goofy_irc_be.repository.GeneralReportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class GeneralReportService {
    private static final Logger log = LoggerFactory.getLogger(GeneralReportService.class);

    private final GeneralReportRepository generalReportRepository;

    public GeneralReportService(GeneralReportRepository generalReportRepository) {
        this.generalReportRepository = generalReportRepository;
    }

    public void submitReport(GeneralReportDto reportDto, String optHandle) {
        log.info("Received Report: {} from {}", reportDto, optHandle);
        GeneralReport report = new GeneralReport();
        report.setTitle(reportDto.getTitle());
        report.setDescription(reportDto.getDescription());
        report.setContact(reportDto.getContact());
        report.setOptionalHandle(optHandle);
        report.setCreatedAt(Instant.now());
        generalReportRepository.save(report);
    }

    public List<GeneralReport> getAllReports() {
        return generalReportRepository.findAll();
    }

    public List<GeneralReport> getAllUnresolvedReports() {
        return generalReportRepository.findAllByResolvedAtIsNull();
    }

    public GeneralReport getReportById(Long id) throws GenericNotFound {
        return generalReportRepository.findById(id).orElseThrow(() -> new GenericNotFound(id));
    }

    public void deleteReport(Long id) {
        generalReportRepository.deleteById(id);
    }

    public void setResolvedStatus(Long id, boolean resolved) throws GenericNotFound {
        GeneralReport report = generalReportRepository.findById(id)
                .orElseThrow(() -> new GenericNotFound(id));

        report.setResolvedAt(resolved ? Instant.now() : null);
        generalReportRepository.save(report);
    }
}
