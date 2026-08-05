package com.gamebuddy.shared.repository;

import com.gamebuddy.shared.entity.Avatars;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** The stock profile pictures. All free; see {@link Avatars}. */
@Repository
public interface AvatarsRepository extends JpaRepository<Avatars, UUID> {

    /**
     * One query for a page's worth of avatars.
     *
     * <p>Rendering a list of gamers previously issued one findById per row, so a
     * fifty-gamer page cost fifty extra round trips.
     */
    List<Avatars> findAllByIdIn(Collection<UUID> ids);
}
