package com.gamebuddy.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gamebuddy.shared.entity.AvatarStatus;
import com.gamebuddy.shared.entity.Avatars;
import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.repository.AvatarsRepository;
import com.gamebuddy.shared.storage.AvatarUrls;
import com.gamebuddy.shared.storage.ObjectStorage;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The single rule for which picture a gamer shows.
 *
 * <p>This used to be four private copies, one per module. The test that matters most is
 * the first one: an uploaded image is invisible until it has been approved. Getting that
 * wrong in one of the four copies would have published an unreviewed picture on the
 * community feed while the profile screen correctly hid it, and nothing would have caught
 * it.
 */
@ExtendWith(MockitoExtension.class)
class AvatarUrlsTest {

    @Mock
    ObjectStorage storage;

    @Mock
    AvatarsRepository avatarsRepository;

    AvatarUrls avatarUrls;

    @BeforeEach
    void setUp() {
        avatarUrls = new AvatarUrls(storage, avatarsRepository);
        lenient().when(storage.publicUrl(any())).thenAnswer(i -> "https://cdn.test/" + i.getArgument(0));
    }

    private static Gamer gamer(String id) {
        Gamer g = new Gamer();
        g.setUserId(id);
        return g;
    }

    @Test
    @DisplayName("a pending upload is invisible to everyone else")
    void pendingIsHidden() {
        Gamer g = gamer("g1");
        g.setAvatarKey("avatars/g1/a.jpg");
        g.setAvatarStatus(AvatarStatus.PENDING);

        assertThat(avatarUrls.visibleTo(g)).isNull();
    }

    @Test
    @DisplayName("a rejected upload is invisible to everyone else")
    void rejectedIsHidden() {
        Gamer g = gamer("g1");
        g.setAvatarKey("avatars/g1/a.jpg");
        g.setAvatarStatus(AvatarStatus.REJECTED);

        assertThat(avatarUrls.visibleTo(g)).isNull();
    }

    @Test
    void anApprovedUploadIsShown() {
        Gamer g = gamer("g1");
        g.setAvatarKey("avatars/g1/a.jpg");
        g.setAvatarStatus(AvatarStatus.APPROVED);

        assertThat(avatarUrls.visibleTo(g)).isEqualTo("https://cdn.test/avatars/g1/a.jpg");
    }

    @Test
    @DisplayName("the owner sees their own upload while it is still under review")
    void ownerSeesTheirPendingUpload() {
        Gamer g = gamer("g1");
        g.setAvatarKey("avatars/g1/a.jpg");
        g.setAvatarStatus(AvatarStatus.PENDING);

        // Hiding it from the uploader as well would make the wait look like a failure.
        assertThat(avatarUrls.visibleToOwner(g)).isEqualTo("https://cdn.test/avatars/g1/a.jpg");
    }

    @Test
    @DisplayName("an account with no upload falls back to the catalogue default")
    void catalogueFallback() {
        UUID id = UUID.randomUUID();
        Gamer g = gamer("g1");
        g.setAvatar(id);

        Avatars catalogue = new Avatars();
        catalogue.setId(id);
        catalogue.setImage("default-avatars/avatar-03.png");
        when(avatarsRepository.findById(id)).thenReturn(Optional.of(catalogue));

        // A key, resolved through storage — not a URL stored in the row.
        assertThat(avatarUrls.visibleTo(g)).isEqualTo("https://cdn.test/default-avatars/avatar-03.png");
    }

    @Test
    @DisplayName("a full URL left over from the Firebase era is passed through untouched")
    void legacyAbsoluteUrl() {
        UUID id = UUID.randomUUID();
        Gamer g = gamer("g1");
        g.setAvatar(id);

        Avatars legacy = new Avatars();
        legacy.setId(id);
        legacy.setImage("https://old.example/avatar.png");
        when(avatarsRepository.findById(id)).thenReturn(Optional.of(legacy));

        assertThat(avatarUrls.visibleTo(g)).isEqualTo("https://old.example/avatar.png");
    }

    @Test
    @DisplayName("an account with neither gets null, so the client draws its monogram")
    void nothingAtAll() {
        assertThat(avatarUrls.visibleTo(gamer("g1"))).isNull();
    }

    @Test
    @DisplayName("an approved upload wins over a legacy catalogue avatar")
    void uploadBeatsLegacy() {
        Gamer g = gamer("g1");
        g.setAvatar(UUID.randomUUID());
        g.setAvatarKey("avatars/g1/a.jpg");
        g.setAvatarStatus(AvatarStatus.APPROVED);

        assertThat(avatarUrls.visibleTo(g)).isEqualTo("https://cdn.test/avatars/g1/a.jpg");
        verify(avatarsRepository, never()).findById(any());
    }

    @Test
    @DisplayName("a batch costs one catalogue query however many gamers it holds")
    void batchIsOneQuery() {
        List<Gamer> gamers = List.of(gamer("g1"), gamer("g2"), gamer("g3"));
        gamers.forEach(g -> g.setAvatar(UUID.randomUUID()));
        when(avatarsRepository.findAllByIdIn(anySet())).thenReturn(List.of());

        avatarUrls.visibleTo(gamers);

        // The N+1 this replaced turned a twenty-row feed into twenty-one round trips.
        verify(avatarsRepository, times(1)).findAllByIdIn(anySet());
        verify(avatarsRepository, never()).findById(any());
    }

    @Test
    @DisplayName("a batch does not look up the catalogue for gamers who have an upload")
    void batchSkipsTheCatalogueForUploads() {
        Gamer uploaded = gamer("g1");
        uploaded.setAvatarKey("avatars/g1/a.jpg");
        uploaded.setAvatarStatus(AvatarStatus.APPROVED);

        Map<String, String> resolved = avatarUrls.visibleTo(List.of(uploaded));

        assertThat(resolved).containsEntry("g1", "https://cdn.test/avatars/g1/a.jpg");
        verify(avatarsRepository, never()).findAllByIdIn(anySet());
    }

    @Test
    @DisplayName("a batch omits anyone with nothing to show rather than mapping them to null")
    void batchOmitsTheEmpty() {
        assertThat(avatarUrls.visibleTo(List.of(gamer("g1")))).isEmpty();
    }
}
