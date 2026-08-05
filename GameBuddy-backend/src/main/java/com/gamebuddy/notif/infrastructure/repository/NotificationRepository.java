package com.gamebuddy.notif.infrastructure.repository;

import com.gamebuddy.notif.infrastructure.entity.Notification;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /**
     * A gamer's own notifications plus every broadcast, newest first.
     *
     * <p>Replaces two separate queries whose results were concatenated in memory with no
     * ordering at all, so the history came back in whatever order the database chose and
     * the broadcasts were always bunched at the end.
     */
    @Query("""
            select n from Notification n
            where (n.isTopic = false and n.recipient = :userId)
               or n.isTopic = true
            order by n.createdDate desc
            """)
    List<Notification> findVisibleTo(@Param("userId") String userId, Pageable pageable);
}
