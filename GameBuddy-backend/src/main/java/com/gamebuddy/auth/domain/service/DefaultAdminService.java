package com.gamebuddy.auth.domain.service;

import com.gamebuddy.auth.application.mapper.AdminCatalogueMapper;
import com.gamebuddy.auth.application.mapper.GamerMapper;
import com.gamebuddy.auth.infrastructure.entity.*;
import com.gamebuddy.auth.infrastructure.repository.*;
import com.gamebuddy.auth.interfaces.dto.GamerDto;
import com.gamebuddy.auth.interfaces.dto.GamerResponseBody;
import com.gamebuddy.auth.interfaces.request.GameRequest;
import com.gamebuddy.auth.interfaces.request.KeywordRequest;
import com.gamebuddy.auth.interfaces.response.GamerResponse;
import com.gamebuddy.common.base.BaseBody;
import com.gamebuddy.common.base.Status;
import com.gamebuddy.common.enums.Role;
import com.gamebuddy.common.enums.TransactionCode;
import com.gamebuddy.common.exception.BusinessException;
import com.gamebuddy.common.interfaces.DefaultMessageResponse;
import com.gamebuddy.shared.entity.*;
import com.gamebuddy.shared.repository.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultAdminService implements AdminService {

    private final GamerRepository gamerRepository;
    private final AvatarsRepository avatarsRepository;
    private final SessionRepository sessionRepository;
    private final GamesRepository gamesRepository;
    private final KeywordsRepository keywordsRepository;
    private final GamerMapper gamerMapper;
    private final AdminCatalogueMapper adminCatalogueMapper;

    @Override
    @Transactional(readOnly = true)
    public GamerResponse getBlockedUsers(Gamer principal) {
        requireAdmin(principal);

        List<GamerDto> blockedUsers = gamerRepository.findAllByIsBlockedTrue().stream()
                .map(this::toGamerDtoWithAvatar)
                .toList();

        GamerResponse response = new GamerResponse();
        GamerResponseBody body = new GamerResponseBody();
        body.setBlockedUsers(blockedUsers);
        response.setBody(new BaseBody<>(body));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        return response;
    }

    @Override
    @Transactional
    public DefaultMessageResponse banUser(Gamer principal, String userId) {
        requireAdmin(principal);
        Gamer user = gamerRepository
                .findById(userId)
                .orElseThrow(() -> new BusinessException(TransactionCode.USER_NOT_FOUND));

        user.setIsBlocked(true);
        // Kills every outstanding token immediately. Deleting the session row alone did
        // nothing, because no service ever consulted the session table on a request.
        user.revokeIssuedTokens();
        gamerRepository.save(user);

        // Single bulk statement. The old code did findByEmail(...).orElse(new Session())
        // then delete(), handing a transient entity with a null id to the EntityManager
        // whenever the user had no active session.
        int removed = sessionRepository.deleteAllByEmail(user.getEmail());
        log.info("Banned {} and dropped {} session(s)", user.getUserId(), removed);

        return DefaultMessageResponse.of("User " + user.getGamerUsername() + " blocked successfully");
    }

    @Override
    @Transactional
    public DefaultMessageResponse unbanUser(Gamer principal, String userId) {
        requireAdmin(principal);
        Gamer user = gamerRepository
                .findById(userId)
                .orElseThrow(() -> new BusinessException(TransactionCode.USER_NOT_FOUND));

        if (!Boolean.TRUE.equals(user.getIsBlocked())) {
            throw new BusinessException(TransactionCode.USER_NOT_BLOCKED);
        }
        user.setIsBlocked(false);
        gamerRepository.save(user);
        return DefaultMessageResponse.of("User " + user.getGamerUsername() + " unblocked successfully");
    }

    @Override
    @Transactional
    public DefaultMessageResponse addGame(Gamer principal, GameRequest gameRequest) {
        requireAdmin(principal);

        Games game = adminCatalogueMapper.toEntity(gameRequest);
        gamesRepository.save(game);

        return DefaultMessageResponse.of("Game '" + game.getGameName() + "' added successfully");
    }

    @Override
    @Transactional
    public DefaultMessageResponse addKeyword(Gamer principal, KeywordRequest keywordRequest) {
        requireAdmin(principal);

        Keywords keyword = adminCatalogueMapper.toEntity(keywordRequest);
        keywordsRepository.save(keyword);

        return DefaultMessageResponse.of("Keyword '" + keyword.getKeywordName() + "' added successfully");
    }

    /**
     * Maps the gamer, then resolves the avatar image the mapper deliberately leaves out.
     *
     * <p>findById(null) throws InvalidDataAccessApiUsageException, and a gamer who never
     * finished onboarding has no avatar yet.
     */
    private GamerDto toGamerDtoWithAvatar(Gamer gamer) {
        GamerDto dto = gamerMapper.toDto(gamer);
        if (gamer.getAvatar() != null) {
            dto.setAvatar(avatarsRepository
                    .findById(gamer.getAvatar())
                    .map(Avatars::getImage)
                    .orElse(null));
        }
        return dto;
    }

    /**
     * Second line of defence. {@code /admin/**} already requires {@code ROLE_ADMIN} in
     * the filter chain; this keeps the check next to the data and preserves the
     * documented {@code NOT_ADMIN} (140) contract.
     */
    private void requireAdmin(Gamer principal) {
        Gamer admin = gamerRepository
                .findById(principal.getUserId())
                .orElseThrow(() -> new BusinessException(TransactionCode.USER_NOT_FOUND));
        if (admin.getRole() != Role.ADMIN) {
            throw new BusinessException(TransactionCode.NOT_ADMIN);
        }
    }
}
