package com.gamebuddy.community.infrastructure.repository;

import com.gamebuddy.community.infrastructure.entity.Community;
import com.gamebuddy.community.infrastructure.entity.Post;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PostRepository extends JpaRepository<Post, UUID> {

    /**
     * The joined-communities feed, paged and ordered in the database.
     *
     * <p>It used to load every post of every community the gamer had joined into memory
     * and sort the DTO list afterwards, so a member of a few busy communities pulled
     * their entire history on every feed request.
     */
    List<Post> findAllByCommunityInOrderByUpdatedDateDesc(Collection<Community> communities, Pageable pageable);

    List<Post> findAllByCommunityOrderByUpdatedDateDesc(Community community, Pageable pageable);

    /**
     * How many posts a gamer has written, for the badges that count them.
     *
     * <p>{@code owner} is a user id, not a username — see {@code DefaultCommunityService},
     * which sets it from the principal.
     */
    long countByOwner(String owner);
}
