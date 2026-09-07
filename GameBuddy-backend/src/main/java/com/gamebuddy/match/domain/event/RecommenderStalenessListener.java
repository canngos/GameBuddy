package com.gamebuddy.match.domain.event;

import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.event.ProfileChangedEvent;
import com.gamebuddy.shared.repository.GamerRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Records that the trained model's copy of a gamer's profile has gone out of date.
 *
 * <p>{@link ProfileChangedEvent} had no consumer at all. The Javadoc at the publisher
 * described a {@code RecommenderRefreshListener} that was planned and never written, so
 * changing your games did nothing: {@code /predict} ranks a known gamer from features
 * pickled into the artefact at training time, and
 * {@link com.gamebuddy.match.domain.service.DefaultMatchService} only fell back to the
 * cold-start path — the one that reads live games and keywords — when {@code /predict}
 * came back <em>empty</em>. A gamer the model already knew never came back empty, so they
 * kept being ranked on the profile they had abandoned until the next retrain.
 *
 * <p>Retraining per edit is not the fix. It is what the original recommender did, with an
 * algorithm that had no {@code predict} and so had to refit everything to place one gamer;
 * that is O(n^3) and stops completing somewhere in the low tens of thousands of accounts.
 * Marking the gamer costs one column and routes them down a path that already exists.
 *
 * <p>Synchronous and inside the publisher's transaction, deliberately. The mark and the
 * profile edit are the same fact about the same row: if the edit rolls back there is
 * nothing to be stale about, and if the mark cannot be written then neither could the edit.
 * It also means the gamer's very next swipe already sees the fresh ranking, rather than
 * whenever an async listener got round to it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecommenderStalenessListener {

    private final GamerRepository gamerRepository;
    private final Clock clock;

    @EventListener
    public void onProfileChanged(ProfileChangedEvent event) {
        Gamer gamer = gamerRepository.findById(event.userId()).orElse(null);
        if (gamer == null) {
            // Not an error worth failing the edit over: the only way to get here is a
            // deletion racing a profile change, and the deleted account has no feed.
            log.warn("Profile changed for unknown gamer {}", event.userId());
            return;
        }
        gamer.setRecommenderProfileChangedAt(clock.instant());
        gamerRepository.save(gamer);
    }
}
