package com.gamebuddy.moderation.infrastructure.repository;

import com.gamebuddy.moderation.infrastructure.entity.ModerationAction;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ModerationActionRepository extends JpaRepository<ModerationAction, UUID> {

    /** What has already been done to this gamer, newest first. */
    List<ModerationAction> findByTargetIdOrderByCreatedAtDesc(String targetId, Pageable pageable);
}
