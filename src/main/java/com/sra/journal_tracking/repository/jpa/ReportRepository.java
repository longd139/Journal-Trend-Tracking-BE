package com.sra.journal_tracking.repository.jpa;

import com.sra.journal_tracking.entity.jpa.Report;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ReportRepository extends JpaRepository<Report, UUID> {
}
