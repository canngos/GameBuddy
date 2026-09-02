package com.gamebuddy.shared.repository;

import com.gamebuddy.shared.entity.Cosmetic;
import com.gamebuddy.shared.entity.CosmeticKind;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** The frames and banners on sale. */
@Repository
public interface CosmeticRepository extends JpaRepository<Cosmetic, UUID> {

    /**
     * The whole shelf, in shelf order.
     *
     * <p>The catalogue is a couple of dozen rows that change when someone draws new art, so
     * it is fetched whole and grouped in memory rather than paged. If it ever grows past a
     * screenful of scrolling this becomes a paged query per kind.
     */
    List<Cosmetic> findAllByOrderByKindAscSortOrderAsc();

    List<Cosmetic> findAllByKindOrderBySortOrderAsc(CosmeticKind kind);

    /** For resolving the frames and banners worn by a page of gamers in one query. */
    List<Cosmetic> findAllByIdIn(Collection<UUID> ids);

    /**
     * The cosmetic a badge unlocks, if it unlocks one.
     *
     * <p>Optional rather than a list: a partial unique index makes at most one row point at
     * any badge, because "what does this badge give me" is a question that must not have
     * two answers.
     */
    Optional<Cosmetic> findByUnlockedByBadge(String badgeCode);

    /** Every trophy frame, for naming them on the badges board in one query rather than N. */
    List<Cosmetic> findAllByUnlockedByBadgeIsNotNull();
}
