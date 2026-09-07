package com.gamebuddy.shared.repository;

import com.gamebuddy.common.enums.LinkVisibility;
import com.gamebuddy.common.enums.LinkedProvider;
import com.gamebuddy.shared.entity.GamerLinkedAccount;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Linked Discord accounts.
 *
 * <p>In {@code shared} rather than in {@code auth} because both modules need it and for
 * opposite reasons: auth writes links, profile reads them onto a profile response. A
 * repository in either one would have the other reaching across a module boundary — see
 * {@code ModuleBoundaryTest}, which permits exactly this arrangement and forbids that one.
 */
@Repository
public interface GamerLinkedAccountRepository extends JpaRepository<GamerLinkedAccount, GamerLinkedAccount.Key> {

    /**
     * Links for a whole page of gamers, in one query.
     *
     * <p>For the deck, which renders up to a page of cards at a time. Called per row this
     * would be the N+1 the rest of that mapping is carefully written to avoid — see the
     * {@code @BatchSize} notes in {@code DefaultMatchService#toDtos}.
     */
    List<GamerLinkedAccount> findByGamer_UserIdInAndVisibility(Collection<String> userIds, LinkVisibility visibility);

    /** Every link a gamer holds. At most one per provider. */
    List<GamerLinkedAccount> findByGamer_UserId(String userId);

    Optional<GamerLinkedAccount> findByGamer_UserIdAndProvider(String userId, LinkedProvider provider);

    /**
     * Who, if anyone, has already claimed this external account.
     *
     * <p>The check behind {@code ACCOUNT_ALREADY_LINKED}. Backed by the unique index, so a
     * race between two simultaneous links loses at the database rather than here.
     */
    Optional<GamerLinkedAccount> findByProviderAndExternalId(LinkedProvider provider, String externalId);

    void deleteByGamer_UserIdAndProvider(String userId, LinkedProvider provider);

    /**
     * Every link a gamer holds, for account deletion.
     *
     * <p>The schema cascades, but deletion here anonymises the row rather than removing it,
     * so nothing would ever fire that cascade — and a surviving row keeps the external
     * account claimed forever against the unique index.
     */
    void deleteAllByGamer_UserId(String userId);
}
