package com.gamebuddy.match.infrastructure.repository;

import com.gamebuddy.match.infrastructure.entity.ChatParticipant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatParticipantRepository extends JpaRepository<ChatParticipant, ChatParticipant.Key> {

    /** Every conversation this gamer is part of. Drives the inbox. */
    List<ChatParticipant> findAllByUserId(String userId);

    /** Both sides of one conversation; used to resolve who the other person is. */
    List<ChatParticipant> findAllByRoomId(UUID roomId);

    Optional<ChatParticipant> findByRoomIdAndUserId(UUID roomId, String userId);
}
