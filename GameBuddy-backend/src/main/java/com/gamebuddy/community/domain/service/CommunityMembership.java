package com.gamebuddy.community.domain.service;

import com.gamebuddy.shared.entity.Gamer;
import java.util.List;
import java.util.UUID;

/**
 * What other modules may ask the community module about membership.
 *
 * <p>Narrow on purpose. The profile screen lists the communities a gamer has joined, which
 * previously meant application-service mapping {@code schcomm.community} with its own
 * entity and walking {@code Gamer.joinedCommunities} — a second module reading another's
 * tables through a second mapping of the same join table. That is precisely the coupling
 * the module boundaries exist to prevent, and it is what let the two mappings disagree
 * about who owned {@code community_members_join}.
 *
 * <p>A record rather than the {@code Community} entity, so nothing outside this module
 * holds a reference to it and no caller can lazily walk into its posts and comments.
 */
public interface CommunityMembership {

    /**
     * The communities this gamer belongs to, newest membership irrelevant — ordered by
     * name so the profile screen is stable between loads.
     */
    List<JoinedCommunity> findJoinedBy(Gamer gamer);

    /**
     * @param id the community
     * @param name display name
     * @param description shown under the name
     * @param avatar image reference, may be null
     * @param owned whether the gamer this was fetched for owns it
     */
    record JoinedCommunity(UUID id, String name, String description, String avatar, boolean owned) {}
}
