package com.gamebuddy.shared.messaging;

/**
 * One push, on its way across the cluster.
 *
 * <p>What gets published to Redis: who it is for, which destination, and the payload
 * already rendered as JSON.
 *
 * <p><b>The payload is a string, not an object.</b> Publishing the object and letting each
 * subscriber deserialise it would put the sender's class names on the wire, so the two
 * instances would have to agree on packages as well as on data — and a rolling deploy, the
 * whole reason for having two, is exactly when they do not. A rendered string means the
 * receiving instance never needs the sending instance's classes.
 *
 * <p>The receiver parses it back into maps and lists before handing it to the broker rather
 * than passing the string through. Passing the string would have Spring serialise it a
 * second time, and the client would receive a JSON string containing escaped JSON instead
 * of an object — the same bytes, one level too deep, which breaks every consumer.
 *
 * @param principalName the recipient's STOMP principal
 * @param destination a user destination such as {@code /queue/messages}
 * @param payloadJson the message body, already serialised
 */
public record SocketDelivery(String principalName, String destination, String payloadJson) {}
