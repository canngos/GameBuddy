package com.gamebuddy.auth.domain.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gamebuddy.auth.application.mapper.*;
import com.gamebuddy.auth.infrastructure.entity.*;
import com.gamebuddy.auth.infrastructure.repository.*;
import com.gamebuddy.auth.interfaces.request.GameRequest;
import com.gamebuddy.auth.interfaces.request.KeywordRequest;
import com.gamebuddy.auth.interfaces.response.GamerResponse;
import com.gamebuddy.common.enums.Role;
import com.gamebuddy.common.exception.BusinessException;
import com.gamebuddy.common.interfaces.DefaultMessageResponse;
import com.gamebuddy.shared.entity.*;
import com.gamebuddy.shared.repository.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DefaultAdminServiceTest {

    @InjectMocks
    private DefaultAdminService adminService;

    @Mock
    private GamerRepository gamerRepository;

    @Mock
    private AvatarsRepository avatarsRepository;

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private GamesRepository gamesRepository;

    @Mock
    private KeywordsRepository keywordsRepository;

    // Real generated implementations rather than mocks: mapping is logic worth
    // exercising, and a mock would just return null and prove nothing.
    @Spy
    private GamerMapper gamerMapper = new GamerMapperImpl();

    @Spy
    private AdminCatalogueMapper adminCatalogueMapper = new AdminCatalogueMapperImpl();

    private Gamer admin;
    private Gamer target;

    @BeforeEach
    void setUp() {
        admin = new Gamer();
        admin.setUserId(UUID.randomUUID().toString());
        admin.setEmail("admin@example.com");
        admin.setGamerUsername("admin");
        admin.setRole(Role.ADMIN);

        target = new Gamer();
        target.setUserId(UUID.randomUUID().toString());
        target.setEmail("victim@example.com");
        target.setGamerUsername("victim");
        target.setRole(Role.USER);
        target.setIsBlocked(false);

        when(gamerRepository.findById(admin.getUserId())).thenReturn(Optional.of(admin));
    }

    @Test
    void testGetBlockedUsers_whenUserNotFound_ReturnErrorCode103() {
        when(gamerRepository.findById(admin.getUserId())).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () -> adminService.getBlockedUsers(admin));
        assertEquals(103, ex.getTransactionCode().getId());
    }

    @Test
    void testGetBlockedUsers_whenCalledByUser_ReturnErrorCode140() {
        admin.setRole(Role.USER);

        BusinessException ex = assertThrows(BusinessException.class, () -> adminService.getBlockedUsers(admin));
        assertEquals(140, ex.getTransactionCode().getId());
    }

    @Test
    void testGetBlockedUsers_whenCalledByAdmin_ReturnBlockedUsers() {
        target.setIsBlocked(true);
        target.setAvatar(UUID.randomUUID());
        Avatars avatar = new Avatars();
        avatar.setId(target.getAvatar());
        avatar.setImage("avatar.png");
        when(gamerRepository.findAllByIsBlockedTrue()).thenReturn(List.of(target));
        when(avatarsRepository.findById(target.getAvatar())).thenReturn(Optional.of(avatar));

        GamerResponse response = adminService.getBlockedUsers(admin);

        assertEquals("100", response.getStatus().getCode());
        assertEquals(1, response.getBody().getData().getBlockedUsers().size());
        assertEquals(
                "avatar.png",
                response.getBody().getData().getBlockedUsers().get(0).getAvatar());
    }

    @Test
    @DisplayName("a blocked user who never chose an avatar does not blow up the listing")
    void testGetBlockedUsers_whenAvatarIsNull_ReturnsNullAvatarNotError() {
        target.setIsBlocked(true);
        target.setAvatar(null);
        when(gamerRepository.findAllByIsBlockedTrue()).thenReturn(List.of(target));

        GamerResponse response = adminService.getBlockedUsers(admin);

        assertNull(response.getBody().getData().getBlockedUsers().get(0).getAvatar());
        // findById(null) would have raised InvalidDataAccessApiUsageException.
        verify(avatarsRepository, never()).findById(any());
    }

    @Test
    void testBanUser_whenUserNotFound_ReturnErrorCode103() {
        when(gamerRepository.findById(target.getUserId())).thenReturn(Optional.empty());

        String targetId = target.getUserId();
        BusinessException ex = assertThrows(BusinessException.class, () -> adminService.banUser(admin, targetId));
        assertEquals(103, ex.getTransactionCode().getId());
    }

    @Test
    void testBanUser_whenCalledValid_ReturnSuccessMessage() {
        when(gamerRepository.findById(target.getUserId())).thenReturn(Optional.of(target));
        when(sessionRepository.deleteAllByEmail(target.getEmail())).thenReturn(1);

        DefaultMessageResponse response = adminService.banUser(admin, target.getUserId());

        assertEquals("100", response.getStatus().getCode());
        assertTrue(target.getIsBlocked());
        assertNotNull(target.getTokensValidFrom(), "banning must invalidate outstanding tokens");
        verify(sessionRepository).deleteAllByEmail(target.getEmail());
    }

    @Test
    @DisplayName("banning a user with no active session is a no-op, not a transient-entity failure")
    void testBanUser_whenNoActiveSession_StillSucceeds() {
        when(gamerRepository.findById(target.getUserId())).thenReturn(Optional.of(target));
        when(sessionRepository.deleteAllByEmail(target.getEmail())).thenReturn(0);

        assertEquals(
                "100",
                adminService.banUser(admin, target.getUserId()).getStatus().getCode());
        // The old code called delete() on `new Session()` with a null id here.
        verify(sessionRepository, never()).delete(any());
    }

    @Test
    void testUnbanUser_whenUserNotFound_ReturnErrorCode103() {
        when(gamerRepository.findById(target.getUserId())).thenReturn(Optional.empty());

        String targetId = target.getUserId();
        BusinessException ex = assertThrows(BusinessException.class, () -> adminService.unbanUser(admin, targetId));
        assertEquals(103, ex.getTransactionCode().getId());
    }

    @Test
    void testUnbanUser_whenUserNotBanned_ReturnErrorCode119() {
        when(gamerRepository.findById(target.getUserId())).thenReturn(Optional.of(target));

        String targetId = target.getUserId();
        BusinessException ex = assertThrows(BusinessException.class, () -> adminService.unbanUser(admin, targetId));
        assertEquals(119, ex.getTransactionCode().getId());
    }

    @Test
    void testUnbanUser_whenCalledValid_ReturnSuccessMessage() {
        target.setIsBlocked(true);
        when(gamerRepository.findById(target.getUserId())).thenReturn(Optional.of(target));

        DefaultMessageResponse response = adminService.unbanUser(admin, target.getUserId());

        assertEquals("100", response.getStatus().getCode());
        assertFalse(target.getIsBlocked());
    }

    @Test
    void testAddGame_whenAdminAddsGame_ReturnSuccessMessage() {
        GameRequest request = new GameRequest();
        request.setGameName("Valorant");
        request.setGameDescription("Tactical shooter");
        request.setCategory("FPS");
        request.setGameIcon("icon.png");
        request.setRating(4.5f);

        DefaultMessageResponse response = adminService.addGame(admin, request);

        assertEquals("100", response.getStatus().getCode());
        verify(gamesRepository).save(any(Games.class));
    }

    @Test
    void testAddKeyword_whenAdminAddsKeyword_ReturnSuccessMessage() {
        KeywordRequest request = new KeywordRequest();
        request.setKeyword("Competitive");
        request.setDescription("Plays to win");

        DefaultMessageResponse response = adminService.addKeyword(admin, request);

        assertEquals("100", response.getStatus().getCode());
        verify(keywordsRepository).save(any(Keywords.class));
    }
}
