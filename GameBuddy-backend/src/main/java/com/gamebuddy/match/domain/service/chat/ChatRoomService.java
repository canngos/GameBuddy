package com.gamebuddy.match.domain.service.chat;

import com.gamebuddy.match.infrastructure.entity.ChatParticipant;
import com.gamebuddy.match.infrastructure.entity.ChatRoom;
import com.gamebuddy.match.infrastructure.repository.ChatParticipantRepository;
import com.gamebuddy.match.infrastructure.repository.ChatRoomRepository;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Finds and opens conversations.
 *
 * <p>One row per conversation, keyed by the sorted pair of user ids. The Mongo version
 * wrote two documents per room — one for each direction, both carrying the same chat id —
 * so every room existed twice, the inbox only ever found the copy where the caller happened
 * to be the {@code sender}, and nothing kept the two halves consistent.
 */
@Service
@RequiredArgsConstructor
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatParticipantRepository participantRepository;
    private final Clock clock;

    /** The existing conversation between two gamers, if there is one. */
    @Transactional(readOnly = true)
    public Optional<ChatRoom> find(String userA, String userB) {
        return chatRoomRepository.findByPairKey(ChatRoom.pairKeyFor(userA, userB));
    }

    /**
     * The conversation between two gamers, created if it does not exist.
     *
     * <p>Both participant rows are written with it, so the inbox and the unread count have
     * something to read from the first message onward.
     */
    @Transactional
    public ChatRoom findOrCreate(String userA, String userB) {
        String pairKey = ChatRoom.pairKeyFor(userA, userB);
        return chatRoomRepository.findByPairKey(pairKey).orElseGet(() -> {
            ChatRoom room = new ChatRoom();
            room.setId(UUID.randomUUID());
            room.setPairKey(pairKey);
            room.setCreatedAt(clock.instant());
            chatRoomRepository.save(room);

            participantRepository.save(new ChatParticipant(room.getId(), userA));
            participantRepository.save(new ChatParticipant(room.getId(), userB));
            return room;
        });
    }
}
