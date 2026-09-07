package com.gamebuddy.match.interfaces.dto;

/**
 * Somebody is typing to you.
 *
 * <p>Carries no text and is never stored — it says only that a keystroke happened, which is
 * the whole of what a typing indicator is allowed to reveal.
 *
 * <p>There is no matching "stopped typing" event. The client expires the indicator on a
 * timer instead, because the reliable signal is the one that arrives: a "stopped" frame is
 * exactly what gets lost when somebody's connection drops mid-sentence, and an indicator
 * that waits for it stays on screen forever.
 *
 * @param senderId who is typing
 */
public record TypingNotification(String senderId) {}
