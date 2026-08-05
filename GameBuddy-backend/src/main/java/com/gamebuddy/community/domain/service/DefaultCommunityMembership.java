package com.gamebuddy.community.domain.service;

import com.gamebuddy.community.infrastructure.repository.CommunityRepository;
import com.gamebuddy.shared.entity.Gamer;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The community module's answer to "what has this gamer joined?". */
@Service
@RequiredArgsConstructor
public class DefaultCommunityMembership implements CommunityMembership {

    private final CommunityRepository communityRepository;

    @Override
    @Transactional(readOnly = true)
    public List<JoinedCommunity> findJoinedBy(Gamer gamer) {
        return communityRepository.findAllByMembersContaining(gamer).stream()
                .sorted(Comparator.comparing(c -> c.getName() == null ? "" : c.getName()))
                .map(community -> new JoinedCommunity(
                        community.getCommunityId(),
                        community.getName(),
                        community.getDescription(),
                        community.getCommunityAvatar(),
                        community.getOwner() != null
                                && community.getOwner().getUserId().equals(gamer.getUserId())))
                .toList();
    }
}
