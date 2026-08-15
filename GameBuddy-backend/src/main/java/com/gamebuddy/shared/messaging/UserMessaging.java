package com.gamebuddy.shared.messaging;

/**
 * Sends one message to one gamer's socket, wherever that socket happens to be.
 *
 * <p>Everything that pushes over STOMP goes through here rather than calling
 * {@code SimpMessagingTemplate} directly, and the reason is the horizon rather than today.
 * Spring's simple broker holds its subscriptions in the memory of one process, so
 * {@code convertAndSendToUser} can only reach sockets this instance is holding. With one
 * instance that is every socket and the distinction does not exist. With two, a message
 * from a gamer on instance A to a gamer on instance B is written to the database and then
 * delivered to nobody — the send is a silent no-op, exactly as it is for a recipient who
 * is genuinely offline, so nothing anywhere reports a problem.
 *
 * <p>That failure is invisible in every test that runs one process, which is all of them,
 * and invisible in production until the day a second instance is started. Routing through
 * an interface now means the day it happens is a configuration change rather than an
 * archaeology exercise across three call sites.
 *
 * @see LocalUserMessaging the one-instance implementation, and the default
 * @see RedisUserMessaging the fan-out, active as soon as a Redis host is configured
 */
public interface UserMessaging {

    /**
     * Delivers to a gamer if they are connected anywhere, and does nothing if they are not.
     *
     * <p>Best effort by design, and every caller depends on that. A chat message is
     * persisted before this runs and read back from the database when the recipient next
     * opens the conversation; typing and presence are worthless a second later. Nothing
     * here retries, queues or reports failure, because there is no caller for whom a
     * failed push is worth interrupting.
     *
     * @param principalName the recipient's STOMP principal, which in this application is
     *     their email — see {@code StompAuthChannelInterceptor}
     * @param destination a user destination such as {@code /queue/messages}
     * @param payload serialised as JSON
     */
    void sendToUser(String principalName, String destination, Object payload);
}
