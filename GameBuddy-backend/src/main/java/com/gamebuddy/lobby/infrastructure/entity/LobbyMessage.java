package com.gamebuddy.lobby.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One line of lobby chat.
 *
 * <p>Encrypted at rest exactly as {@code ChatMessage} is — AES-GCM through
 * {@code MessageCipher}, readable by the server, not end-to-end — but its own table: the
 * lobby itself is the room, so there is no room table, no pair key, and no matched-pair
 * precondition to inherit.
 *
 * <p>Rows are deleted when the sweeper archives the lobby. The conversation had a shelf
 * life by design; a plan for last Tuesday's game is not worth storing forever.
 */
@Entity
@Table(name = "lobby_message")
@Getter
@Setter
@NoArgsConstructor
public class LobbyMessage implements Serializable {

    @Id
    private UUID id;

    @Column(name = "lobby_id", nullable = false)
    private UUID lobbyId;

    @Column(name = "sender_id", nullable = false)
    private String senderId;

    /** AES-GCM ciphertext. Never read directly; go through {@code MessageCipher}. */
    @Column(nullable = false)
    private byte[] body;

    @Column(nullable = false)
    private byte[] nonce;

    @Column(name = "key_version", nullable = false)
    private short keyVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
