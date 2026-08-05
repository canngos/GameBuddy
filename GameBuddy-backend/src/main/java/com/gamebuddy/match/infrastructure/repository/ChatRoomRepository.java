package com.gamebuddy.match.infrastructure.repository;

import com.gamebuddy.match.infrastructure.entity.ChatRoom;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatRoomRepository extends JpaRepository<ChatRoom, UUID> {

    /** One unique-index read, whichever way round the pair is given. */
    Optional<ChatRoom> findByPairKey(String pairKey);
}
