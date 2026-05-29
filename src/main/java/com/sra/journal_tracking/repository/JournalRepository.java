package com.sra.journal_tracking.repository;

import com.sra.journal_tracking.entity.Journal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface JournalRepository extends JpaRepository<Journal, UUID> {
}
