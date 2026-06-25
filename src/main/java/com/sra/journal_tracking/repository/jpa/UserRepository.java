package com.sra.journal_tracking.repository.jpa;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.sra.journal_tracking.entity.jpa.User;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    @EntityGraph(attributePaths = {"role"})
    @Query("""
            SELECT u FROM User u
            WHERE (:search IS NULL OR :search = ''
                OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(COALESCE(u.institution, '')) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<User> searchUsers(@Param("search") String search, Pageable pageable);
}
