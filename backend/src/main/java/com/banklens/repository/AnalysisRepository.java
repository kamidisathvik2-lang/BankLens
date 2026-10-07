package com.banklens.repository;

import com.banklens.entity.Analysis;
import com.banklens.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AnalysisRepository extends JpaRepository<Analysis, UUID> {

    Page<Analysis> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

    Optional<Analysis> findByIdAndUser(UUID id, User user);

    @Query("SELECT COUNT(a) FROM Analysis a WHERE a.user = :user AND a.createdAt >= :since")
    long countByUserSince(@Param("user") User user, @Param("since") Instant since);
}
