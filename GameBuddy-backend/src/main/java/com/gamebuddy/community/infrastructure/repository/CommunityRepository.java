package com.gamebuddy.community.infrastructure.repository;

import com.gamebuddy.community.infrastructure.entity.Community;
import com.gamebuddy.shared.entity.Gamer;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CommunityRepository extends JpaRepository<Community, UUID> {

    /**
     * The communities a gamer belongs to.
     *
     * <p>Queried rather than read off {@code Gamer}, because {@link Community#getMembers()}
     * is now the single owning side of {@code community_members_join}. It used to be mapped
     * from both ends — {@code Gamer.joinedCommunities} and {@code Community.members} each
     * declared their own {@code @JoinTable} over the same table with no {@code mappedBy}
     * between them — so Hibernate treated them as two unrelated relationships, and the
     * service wrote to both. Joining a community issued two inserts of the same row: a
     * constraint violation, or a duplicated membership where there was no constraint.
     *
     * <p>Folding the five copies of {@code Gamer} into one is what forced the question of
     * which side owns this. The answer is the community.
     */
    List<Community> findAllByMembersContaining(Gamer gamer);
}
