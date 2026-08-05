package com.gamebuddy.match.infrastructure.repository;

import com.gamebuddy.match.infrastructure.entity.ChatMessage;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    /** A conversation, oldest first. Served by idx_chat_message_room_time. */
    List<ChatMessage> findByRoomIdOrderByCreatedAtAsc(UUID roomId);

    /**
     * The newest message in a room, for the inbox preview.
     *
     * <p>Paged to one rather than fetching the conversation and taking the last: an inbox
     * with twenty rooms would otherwise read every message the gamer has ever exchanged.
     */
    Optional<ChatMessage> findFirstByRoomIdOrderByCreatedAtDescIdDesc(UUID roomId);

    /**
     * Unread count: messages in this room, from someone else, sent after {@code since}.
     *
     * <p>{@code since} must not be null. A conversation that has never been opened has no
     * watermark, and the caller passes {@link java.time.Instant#EPOCH} for that case rather
     * than a null — an earlier version wrote {@code (:since IS NULL OR ...)} and Postgres
     * rejected it with "could not determine data type of parameter", because a bare
     * parameter compared only against NULL gives the planner nothing to infer a type from.
     * Translating the absence in Java keeps the SQL simple and portable.
     */
    @Query("""
            SELECT COUNT(m) FROM ChatMessage m
            WHERE m.roomId = :roomId
              AND m.senderId <> :userId
              AND m.createdAt > :since
            """)
    long countUnread(@Param("roomId") UUID roomId, @Param("userId") String userId, @Param("since") Instant since);

    /** The moderation queue, newest report first. */
    List<ChatMessage> findByReportedAtIsNotNullOrderByReportedAtDesc(Pageable pageable);

    /** How many messages a gamer has sent, over every room, for the chat badges. */
    long countBySenderId(String senderId);
}
