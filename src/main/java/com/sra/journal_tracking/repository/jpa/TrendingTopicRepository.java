package com.sra.journal_tracking.repository.jpa;

import com.sra.journal_tracking.entity.jpa.TrendingTopic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TrendingTopicRepository extends JpaRepository<TrendingTopic, UUID> {

    List<TrendingTopic> findAllByOrderByDisplayOrderAsc();

    void deleteAll();
}
