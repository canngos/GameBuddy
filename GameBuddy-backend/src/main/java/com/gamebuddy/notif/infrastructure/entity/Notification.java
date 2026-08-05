package com.gamebuddy.notif.infrastructure.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * A delivered notification, kept so the client can show a history.
 *
 * <p>{@code recipient} holds either a gamer id or a topic name. That overloading is
 * inherited — the column was called {@code userId} and a broadcast wrote the topic name
 * into it, so the field actively lied about half its contents. {@link #isTopic}
 * distinguishes the two instead of leaving it to a magic-string comparison.
 */
@Entity
@Table(
        name = "notifications",
        indexes = @Index(name = "idx_notification_recipient", columnList = "recipient, createdDate"))
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Notification {

    // Hibernate generates the value, so the column cannot be insertable = false: that
    // told Hibernate to leave it out of the INSERT and let the database default it,
    // while simultaneously asking Hibernate to generate it.
    @Id
    @GeneratedValue
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(length = 1000)
    private String body;

    @CreationTimestamp
    private Instant createdDate;

    /** A gamer id, or a topic name when {@link #isTopic} is true. */
    @Column(name = "recipient", nullable = false)
    private String recipient;

    @Column(nullable = false)
    private Boolean isTopic = Boolean.FALSE;
}
