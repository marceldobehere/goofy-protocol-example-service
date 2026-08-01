package com.masl.goofy_irc_be.repository;

import com.masl.goofy_irc_be.entity.GeneralReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GeneralReportRepository extends JpaRepository<GeneralReport, Long> {
    List<GeneralReport> findAllByResolvedAtIsNull();
}
