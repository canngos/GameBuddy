package com.gamebuddy.match.domain.event;

import com.gamebuddy.match.infrastructure.repository.DeclinedMatchRepository;
import com.gamebuddy.match.infrastructure.repository.RecommendationImpressionRepository;
import com.gamebuddy.shared.event.AccountDeletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Erases a deleted gamer's impression history.
 *
 * <p>{@code recommendation_impression} records who was shown to whom. That is behavioural
 * data about identifiable people in both directions — who this gamer was offered, and who
 * was offered this gamer — so deleting the account has to take it with them. Anonymising
 * the {@code gamer} row is not enough when another table still holds the user id.
 *
 * <p>Synchronous, inside the deleting transaction: an account reported as deleted while its
 * data survives is worse than a deletion that fails and says so.
 *
 * <p>Chat messages are deliberately <em>not</em> deleted here. A conversation belongs to two
 * people, and erasing one side's messages would destroy the other's history — which they
 * did not consent to lose. The sender row is already anonymised, so the messages no longer
 * identify anyone.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImpressionPurgeListener {

    private final RecommendationImpressionRepository impressions;
    private final DeclinedMatchRepository declinedMatches;

    @EventListener
    public void onAccountDeleted(AccountDeletedEvent event) {
        int purgedImpressions = impressions.deleteAllInvolving(event.userId());
        // Declines name both people too: "this account passed over that one" is a record
        // about the person who was passed over as much as the one who passed.
        int purgedDeclines = declinedMatches.deleteAllInvolving(event.userId());
        log.info(
                "Purged {} impression(s) and {} decline(s) for deleted account {}",
                purgedImpressions,
                purgedDeclines,
                event.userId());
    }
}
