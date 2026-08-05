package com.gamebuddy.match.domain.event;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gamebuddy.match.infrastructure.repository.DeclinedMatchRepository;
import com.gamebuddy.match.infrastructure.repository.RecommendationImpressionRepository;
import com.gamebuddy.shared.event.AccountDeletedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Deleting an account has to take the impression log with it.
 *
 * <p>{@code recommendation_impression} records who was shown to whom, which is behavioural
 * data about identifiable people in both directions. Anonymising the {@code gamer} row is
 * not enough while another table still holds the raw user id.
 */
class ImpressionPurgeListenerTest {

    private final RecommendationImpressionRepository impressions = mock(RecommendationImpressionRepository.class);
    private final DeclinedMatchRepository declines = mock(DeclinedMatchRepository.class);
    private final ImpressionPurgeListener listener = new ImpressionPurgeListener(impressions, declines);

    @Test
    @DisplayName("both directions are purged: who they saw, and who saw them")
    void purgesEverythingInvolvingTheAccount() {
        when(impressions.deleteAllInvolving("u-gone")).thenReturn(42);

        listener.onAccountDeleted(new AccountDeletedEvent("u-gone"));

        verify(impressions).deleteAllInvolving("u-gone");
    }

    @Test
    @DisplayName("declines go too — a pass names the person passed over, not just the passer")
    void purgesDeclines() {
        when(declines.deleteAllInvolving("u-gone")).thenReturn(7);

        listener.onAccountDeleted(new AccountDeletedEvent("u-gone"));

        // deleteAllInvolving covers both columns, so this also erases the record of other
        // people having declined the deleted account.
        verify(declines).deleteAllInvolving("u-gone");
    }

    @Test
    @DisplayName("a failed purge propagates, so the deletion rolls back rather than lying")
    void failurePropagates() {
        when(impressions.deleteAllInvolving("u-gone")).thenThrow(new IllegalStateException("database down"));

        // Deliberately not swallowed, unlike the notification dispatcher. An account
        // reported as deleted while its data survives is worse than a deletion that fails
        // and says so — the user can retry, and nothing has been misreported.
        assertThrows(IllegalStateException.class, () -> listener.onAccountDeleted(new AccountDeletedEvent("u-gone")));
    }
}
