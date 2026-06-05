package com.sra.journal_tracking.repository.jpa;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sra.journal_tracking.entity.jpa.Follow;
import com.sra.journal_tracking.entity.jpa.User;

public interface FollowRepository
        extends JpaRepository<Follow, UUID> {

    List<Follow> findByUser(User user);

    long countByUser(User user);
}