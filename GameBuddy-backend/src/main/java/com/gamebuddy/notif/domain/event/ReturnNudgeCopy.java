package com.gamebuddy.notif.domain.event;

import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.repository.GamerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * What a "come back" notification says.
 *
 * <p>Separate from the scheduler because the scheduling rules and the wording answer to
 * different things: one is a policy about how often it is acceptable to interrupt
 * somebody, the other is about whether the interruption is worth their attention. They
 * change for different reasons and neither should have to be read to understand the other.
 *
 * <p>Every line here names something real that is waiting on the gamer's account. Nothing
 * says "we miss you", nothing invents urgency, and when there is genuinely nothing waiting
 * this returns null and no notification is sent. That last rule is the important one: it
 * is what keeps these worth opening, and an app whose reminders are worth opening does not
 * need to send many.
 */
@Component
@RequiredArgsConstructor
public class ReturnNudgeCopy {

    private final GamerRepository gamerRepository;

    /** Uses their name — it is their account, and a notification that knows it reads less like a broadcast. */
    public String titleFor(Gamer gamer) {
        String name = gamer.getGamerUsername();
        return name == null || name.isBlank() ? "GameBuddy" : name + ", you have something waiting";
    }

    /**
     * The most compelling true thing waiting for this gamer, or null if there is nothing.
     *
     * <p>Ordered by how much it is worth coming back for. Somebody who liked you is a
     * match one tap away; a friend request is a decision only they can make; those are
     * worth an interruption. Anything weaker is not, which is why there is no fallback
     * line — the absence of a reason is a reason not to send.
     */
    public String bodyFor(Gamer gamer) {
        int admirers = gamerRepository.findPendingAdmirers(gamer.getUserId()).size();
        if (admirers > 0) {
            return admirers == 1
                    ? "Someone liked you. Like them back and you can start talking."
                    : admirers + " people liked you. Like them back and you can start talking.";
        }

        int requests = gamer.getWaitingFriends().size();
        if (requests > 0) {
            return requests == 1
                    ? "You have a friend request waiting for an answer."
                    : "You have " + requests + " friend requests waiting for an answer.";
        }

        // Nothing is waiting. Better to say nothing than to manufacture a reason.
        return null;
    }
}
