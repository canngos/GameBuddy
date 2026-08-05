package com.gamebuddy.shared.event;

/**
 * A gamer deleted their own account.
 *
 * <p>Modules that hold personal data about them clean it up in response. Published rather
 * than called directly because the auth module has no business knowing which other modules
 * accumulated data — and reaching into their repositories to delete it is exactly the
 * cross-module coupling the boundaries exist to prevent.
 *
 * <p>Listeners run <strong>synchronously, inside the deleting transaction</strong>. That is
 * deliberate and different from the notification event, which is fire-and-forget after
 * commit: a push that fails is a disappointment, whereas a purge that fails silently leaves
 * an account marked deleted with its behavioural data still on disk. If a listener throws,
 * the deletion rolls back and the user can try again — which is the honest outcome.
 *
 * @param userId the account being deleted
 */
public record AccountDeletedEvent(String userId) {}
