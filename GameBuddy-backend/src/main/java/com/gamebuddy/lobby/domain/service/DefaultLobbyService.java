package com.gamebuddy.lobby.domain.service;

import com.gamebuddy.common.base.BaseBody;
import com.gamebuddy.common.base.BaseModel;
import com.gamebuddy.common.base.BaseResponse;
import com.gamebuddy.common.base.Status;
import com.gamebuddy.common.enums.SubscriptionTier;
import com.gamebuddy.common.enums.TransactionCode;
import com.gamebuddy.common.exception.BusinessException;
import com.gamebuddy.common.interfaces.DefaultMessageResponse;
import com.gamebuddy.common.ratelimit.RateLimiter;
import com.gamebuddy.lobby.application.mapper.LobbyMapper;
import com.gamebuddy.lobby.infrastructure.entity.Lobby;
import com.gamebuddy.lobby.infrastructure.entity.LobbyMember;
import com.gamebuddy.lobby.infrastructure.entity.LobbyMemberStatus;
import com.gamebuddy.lobby.infrastructure.entity.LobbyStatus;
import com.gamebuddy.lobby.infrastructure.entity.LobbyTone;
import com.gamebuddy.lobby.infrastructure.repository.LobbyMemberRepository;
import com.gamebuddy.lobby.infrastructure.repository.LobbyMessageRepository;
import com.gamebuddy.lobby.infrastructure.repository.LobbyRepository;
import com.gamebuddy.lobby.interfaces.dto.LobbiesResponseBody;
import com.gamebuddy.lobby.interfaces.dto.LobbyDto;
import com.gamebuddy.lobby.interfaces.dto.LobbyEvent;
import com.gamebuddy.lobby.interfaces.dto.LobbyMemberDto;
import com.gamebuddy.lobby.interfaces.dto.LobbyResponseBody;
import com.gamebuddy.lobby.interfaces.request.CreateLobbyRequest;
import com.gamebuddy.lobby.interfaces.request.UpdateLobbyRequest;
import com.gamebuddy.lobby.interfaces.response.LobbiesResponse;
import com.gamebuddy.lobby.interfaces.response.LobbyResponse;
import com.gamebuddy.shared.coin.CoinLedger;
import com.gamebuddy.shared.coin.CoinReason;
import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.entity.Games;
import com.gamebuddy.shared.event.NotificationKind;
import com.gamebuddy.shared.event.NotificationRequestedEvent;
import com.gamebuddy.shared.messaging.UserMessaging;
import com.gamebuddy.shared.moderation.TextAssessment;
import com.gamebuddy.shared.moderation.TextModerationService;
import com.gamebuddy.shared.moderation.TextSurface;
import com.gamebuddy.shared.repository.GamerRepository;
import com.gamebuddy.shared.repository.GamesRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The lobby lifecycle, driven by the owner and nobody else.
 *
 * <p>The one nuance everything else follows from: <b>locking starts no timer.</b> LOCK
 * means "team found, stop asking" — a team found at four may not play until seven, and
 * nothing here presumes to know when the game happens. The sweeper
 * ({@link LobbyLifecycleJob}) reads the planned time generously, a day late or more,
 * and only ever tidies what the owner visibly abandoned.
 *
 * <p>Cancelling is deliberately unreachable from LOCKED: the owner unlocks first. A formed
 * team is one deliberate step away from an accidental cancel, and unlocking is also the
 * honest signal to pending requesters that seats may be open again.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultLobbyService implements LobbyService {

    /** "Now" must survive clock skew between device and server. */
    static final Duration STARTS_AT_PAST_GRACE = Duration.ofMinutes(15);

    /** Far enough for "raid night next Friday", near enough that the feed stays current. */
    static final Duration STARTS_AT_HORIZON = Duration.ofDays(14);

    /**
     * What the "starting now" filter means: planned to begin within the quarter hour.
     *
     * <p>An upper bound only, deliberately. A lobby whose stated time was twenty minutes
     * ago and which is still OPEN is the most "about to start" thing in the feed —
     * somebody is sitting in it waiting — so a lower bound would hide exactly the lobbies
     * the filter exists to surface.
     */
    static final Duration STARTING_SOON = Duration.ofMinutes(15);

    /**
     * The bound used when "starting now" is off.
     *
     * <p>A sentinel rather than a nullable parameter: it keeps the browse queries to one
     * shape, and {@code startsAt} can never reach it — creation refuses anything beyond
     * {@link #STARTS_AT_HORIZON}.
     */
    private static final Instant NO_START_BOUND = Instant.parse("2999-01-01T00:00:00Z");

    /**
     * What pinning a lobby to the top of the list costs.
     *
     * <p>The same 300 the deck boost charged. It is the most expensive thing on the shelf and
     * about a week and a half of a free player's income, which is the intended shape: what is
     * being bought is other people's attention, and that is finite — cheap boosts would mean
     * everybody boosting, which is the same as nobody boosting.
     */
    static final int LOBBY_BOOST_COST_COINS = 300;

    private static final EnumSet<LobbyStatus> ACTIVE = EnumSet.of(LobbyStatus.OPEN, LobbyStatus.LOCKED);
    private static final EnumSet<LobbyMemberStatus> TEAM =
            EnumSet.of(LobbyMemberStatus.OWNER, LobbyMemberStatus.ACCEPTED);

    private final LobbyRepository lobbyRepository;
    private final LobbyMemberRepository memberRepository;
    private final LobbyMessageRepository messageRepository;
    private final GamerRepository gamerRepository;
    private final GamesRepository gamesRepository;
    private final LobbyMapper mapper;
    private final TextModerationService textModeration;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    private final UserMessaging messaging;
    private final RateLimiter lobbyCreateRateLimiter;
    private final RateLimiter lobbyJoinRateLimiter;
    private final CoinLedger coins;

    @Override
    @Transactional(readOnly = true)
    public LobbiesResponse browse(
            Gamer principal, String gameId, LobbyTone tone, boolean startingSoon, Pageable pageable) {
        Gamer viewer = requireGamer(principal.getUserId());
        Page<Lobby> page = browsePage(gameId, tone, startingSoon, pageable);

        // Blocked pairs never see each other's lobbies, in either direction — the same
        // stance the feed takes. Filtered after the page rather than in SQL: the block
        // relationship lives on the Gamer graph, and a page with a few rows filtered out
        // is cheaper than teaching this query about blocks.
        Map<String, Gamer> owners = byId(gamerRepository.findAllById(
                page.getContent().stream().map(Lobby::getOwnerId).distinct().toList()));
        Map<UUID, LobbyMemberStatus> myRows = myStatuses(viewer.getUserId());

        List<LobbyDto> lobbies = page.getContent().stream()
                .filter(lobby -> {
                    Gamer owner = owners.get(lobby.getOwnerId());
                    return owner != null && !viewer.hasBlockRelationshipWith(owner);
                })
                .map(lobby -> toDto(lobby, owners.get(lobby.getOwnerId()), myRows.get(lobby.getId()), 0))
                .toList();

        LobbiesResponseBody body = new LobbiesResponseBody();
        body.setLobbies(lobbies);
        return respond(new LobbiesResponse(), body);
    }

    @Override
    @Transactional(readOnly = true)
    public LobbiesResponse mine(Gamer principal) {
        Gamer viewer = requireGamer(principal.getUserId());
        List<LobbyMember> rows = memberRepository.findAllByUserIdAndStatusIn(
                viewer.getUserId(),
                EnumSet.of(LobbyMemberStatus.OWNER, LobbyMemberStatus.ACCEPTED, LobbyMemberStatus.PENDING));

        List<LobbyDto> lobbies = rows.stream()
                .flatMap(row -> lobbyRepository.findById(row.getLobbyId()).stream()
                        .filter(lobby -> lobby.getStatus() != LobbyStatus.ARCHIVED)
                        .map(lobby -> {
                            long unread = row.getStatus().inTeam() ? unreadCount(row) : 0;
                            Gamer owner =
                                    gamerRepository.findById(lobby.getOwnerId()).orElse(null);
                            return toDto(lobby, owner, row.getStatus(), unread);
                        }))
                .toList();

        LobbiesResponseBody body = new LobbiesResponseBody();
        body.setLobbies(lobbies);
        return respond(new LobbiesResponse(), body);
    }

    @Override
    @Transactional(readOnly = true)
    public LobbyResponse get(Gamer principal, UUID lobbyId) {
        Gamer viewer = requireGamer(principal.getUserId());
        Lobby lobby = requireVisibleLobby(lobbyId);

        Gamer owner = gamerRepository.findById(lobby.getOwnerId()).orElse(null);
        // Answered as not-found rather than forbidden: "this exists but you may not see
        // it" confirms the lobby exists, which is exactly what a block is meant to end.
        if (owner != null && viewer.hasBlockRelationshipWith(owner) && !lobby.isOwnedBy(viewer.getUserId())) {
            throw new BusinessException(TransactionCode.LOBBY_NOT_FOUND);
        }

        List<LobbyMember> all = memberRepository.findAllByLobbyId(lobbyId);
        Map<String, Gamer> gamers = byId(gamerRepository.findAllById(
                all.stream().map(LobbyMember::getUserId).toList()));

        List<LobbyMemberDto> members = all.stream()
                .filter(m -> m.getStatus().inTeam())
                .map(m -> mapper.toMemberDto(m, gamers.get(m.getUserId())))
                .toList();

        // The pending inbox is the owner's alone. Requesters see their own standing in
        // myStatus; showing the queue to everyone would publish who is asking.
        List<LobbyMemberDto> pending = lobby.isOwnedBy(viewer.getUserId())
                ? all.stream()
                        .filter(m -> m.getStatus() == LobbyMemberStatus.PENDING)
                        .map(m -> mapper.toMemberDto(m, gamers.get(m.getUserId())))
                        .toList()
                : List.of();

        LobbyMemberStatus myStatus = all.stream()
                .filter(m -> m.getUserId().equals(viewer.getUserId()))
                .map(LobbyMember::getStatus)
                .findFirst()
                .orElse(null);

        LobbyResponseBody body = new LobbyResponseBody();
        body.setLobby(toDto(lobby, owner, myStatus, 0));
        body.setMembers(members);
        body.setPendingRequests(pending);
        return respond(new LobbyResponse(), body);
    }

    @Override
    @Transactional
    public LobbyResponse create(Gamer principal, CreateLobbyRequest request) {
        Gamer owner = requireGamer(principal.getUserId());

        // The Gold gate. Browsing and joining are free; opening a lobby is the perk.
        // Effective tier, never the stored column — a lapsed subscriber reads GOLD there
        // forever (see the comment on gamer.subscription_tier).
        SubscriptionTier tier = SubscriptionTier.effective(
                owner.getSubscriptionTier(), owner.getSubscriptionExpiresAt(), clock.instant());
        if (tier != SubscriptionTier.GOLD) {
            throw new BusinessException(TransactionCode.SUBSCRIPTION_REQUIRED);
        }

        // One live lobby per owner. The friendly answer; uq_lobby_active_owner is the
        // backstop for the race this check cannot close.
        if (lobbyRepository.existsByOwnerIdAndStatusIn(owner.getUserId(), ACTIVE)) {
            throw new BusinessException(TransactionCode.LOBBY_LIMIT_REACHED);
        }
        if (!lobbyCreateRateLimiter.tryAcquire(owner.getUserId())) {
            throw new BusinessException(TransactionCode.RATE_LIMITED);
        }

        Games game = gamesRepository
                .findById(request.getGameId())
                .orElseThrow(() -> new BusinessException(TransactionCode.GAME_NOT_FOUND));
        Instant startsAt = requireReasonableStart(request.getStartsAt());

        Lobby lobby = new Lobby();
        lobby.setId(UUID.randomUUID());
        lobby.setOwnerId(owner.getUserId());
        lobby.setGameId(game.getGameId());
        // Screened as PUBLIC text: the browse feed shows these to strangers, the same
        // exposure a community name has.
        lobby.setTitle(screenPublic(request.getTitle()));
        lobby.setDescription(screenPublic(request.getDescription()));
        lobby.setRequirements(screenPublic(request.getRequirements()));
        lobby.setTone(request.getTone());
        lobby.setMaxPlayers(request.getMaxPlayers());
        lobby.setStartsAt(startsAt);
        lobby.setStatus(LobbyStatus.OPEN);
        lobbyRepository.save(lobby);

        // The owner is a member row too, so "everyone in the chat" is one uniform query.
        LobbyMember ownerRow =
                new LobbyMember(lobby.getId(), owner.getUserId(), LobbyMemberStatus.OWNER, clock.instant());
        memberRepository.save(ownerRow);

        LobbyResponseBody body = new LobbyResponseBody();
        body.setLobby(toDto(lobby, owner, LobbyMemberStatus.OWNER, 0));
        body.setMembers(List.of(mapper.toMemberDto(ownerRow, owner)));
        body.setPendingRequests(List.of());
        return respond(new LobbyResponse(), body);
    }

    @Override
    @Transactional
    public DefaultMessageResponse update(Gamer principal, UUID lobbyId, UpdateLobbyRequest request) {
        Gamer owner = requireGamer(principal.getUserId());
        Lobby lobby = requireOwnedLobby(lobbyId, owner);
        if (lobby.getStatus() != LobbyStatus.OPEN) {
            throw new BusinessException(TransactionCode.LOBBY_NOT_OPEN);
        }

        if (request.getTitle() != null) {
            lobby.setTitle(screenPublic(request.getTitle()));
        }
        if (request.getDescription() != null) {
            lobby.setDescription(screenPublic(request.getDescription()));
        }
        if (request.getRequirements() != null) {
            lobby.setRequirements(screenPublic(request.getRequirements()));
        }
        if (request.getTone() != null) {
            lobby.setTone(request.getTone());
        }
        if (request.getStartsAt() != null) {
            lobby.setStartsAt(requireReasonableStart(request.getStartsAt()));
        }
        if (request.getMaxPlayers() != null) {
            // Shrinking below the seats already filled would be a silent kick.
            int taken = (int) memberRepository.countByLobbyIdAndStatusIn(lobbyId, TEAM);
            if (request.getMaxPlayers() < taken) {
                throw new BusinessException(
                        TransactionCode.INVALID_REQUEST, "the team already has " + taken + " players");
            }
            lobby.setMaxPlayers(request.getMaxPlayers());
        }
        lobbyRepository.save(lobby);
        return DefaultMessageResponse.of("Lobby updated successfully");
    }

    @Override
    @Transactional
    public DefaultMessageResponse join(Gamer principal, UUID lobbyId) {
        Gamer requester = requireGamer(principal.getUserId());
        Lobby lobby = requireVisibleLobby(lobbyId);

        if (lobby.getStatus() != LobbyStatus.OPEN) {
            throw new BusinessException(TransactionCode.LOBBY_NOT_OPEN);
        }
        Gamer owner = requireGamer(lobby.getOwnerId());
        if (requester.hasBlockRelationshipWith(owner)) {
            throw new BusinessException(TransactionCode.USER_BLOCKED);
        }
        if (memberRepository.countByLobbyIdAndStatusIn(lobbyId, TEAM) >= lobby.getMaxPlayers()) {
            throw new BusinessException(TransactionCode.LOBBY_FULL);
        }

        Optional<LobbyMember> existing = memberRepository.findByLobbyIdAndUserId(lobbyId, requester.getUserId());
        if (existing.isPresent()) {
            LobbyMember row = existing.get();
            switch (row.getStatus()) {
                case OWNER, ACCEPTED, PENDING -> throw new BusinessException(TransactionCode.LOBBY_ALREADY_MEMBER);
                // A rejection is the owner's final word for this lobby; see LOBBY_REJECTED.
                case REJECTED -> throw new BusinessException(TransactionCode.LOBBY_REJECTED);
                // Someone who left or was kicked may ask again — the row asks again.
                case LEFT, KICKED -> {
                    requireJoinBudget(requester);
                    row.setStatus(LobbyMemberStatus.PENDING);
                    row.setRequestedAt(clock.instant());
                    row.setDecidedAt(null);
                    memberRepository.save(row);
                }
            }
        } else {
            requireJoinBudget(requester);
            memberRepository.save(
                    new LobbyMember(lobbyId, requester.getUserId(), LobbyMemberStatus.PENDING, clock.instant()));
        }

        events.publishEvent(new NotificationRequestedEvent(
                owner.getUserId(),
                owner.getFcmToken(),
                requester.getGamerUsername(),
                "wants to join \"" + lobby.getTitle() + "\"",
                NotificationKind.LOBBY_JOIN_REQUEST,
                lobby.getId().toString()));
        // Live update for an owner sitting on the lobby screen.
        messaging.sendToUser(owner.getEmail(), "/queue/lobby", LobbyEvent.memberEvent(lobbyId.toString()));
        return DefaultMessageResponse.of("Join request sent");
    }

    @Override
    @Transactional
    public DefaultMessageResponse accept(Gamer principal, UUID lobbyId, String userId) {
        Gamer owner = requireGamer(principal.getUserId());
        Lobby lobby = requireOwnedLobby(lobbyId, owner);
        if (lobby.getStatus() != LobbyStatus.OPEN) {
            throw new BusinessException(TransactionCode.LOBBY_NOT_OPEN);
        }

        LobbyMember row = requirePendingRequest(lobbyId, userId);
        if (memberRepository.countByLobbyIdAndStatusIn(lobbyId, TEAM) >= lobby.getMaxPlayers()) {
            throw new BusinessException(TransactionCode.LOBBY_FULL);
        }

        row.setStatus(LobbyMemberStatus.ACCEPTED);
        row.setDecidedAt(clock.instant());
        memberRepository.save(row);
        // Touched so the optimistic lock arbitrates accept-vs-cancel and the race for the
        // last seat: two writers to the same lobby cannot both win.
        lobbyRepository.save(lobby);

        Gamer accepted = requireGamer(userId);
        events.publishEvent(new NotificationRequestedEvent(
                accepted.getUserId(),
                accepted.getFcmToken(),
                lobby.getTitle(),
                "You're in — the team is in the lobby chat",
                NotificationKind.LOBBY_REQUEST_ACCEPTED,
                lobby.getId().toString()));
        fanOutToTeam(lobby, LobbyEvent.memberEvent(lobbyId.toString()));
        return DefaultMessageResponse.of("Request accepted");
    }

    @Override
    @Transactional
    public DefaultMessageResponse reject(Gamer principal, UUID lobbyId, String userId) {
        Gamer owner = requireGamer(principal.getUserId());
        requireOwnedLobby(lobbyId, owner);

        LobbyMember row = requirePendingRequest(lobbyId, userId);
        row.setStatus(LobbyMemberStatus.REJECTED);
        row.setDecidedAt(clock.instant());
        memberRepository.save(row);
        // No push. A rejection notification invites retaliation and carries nothing the
        // requester can act on; their screen shows the answer next time they look.
        return DefaultMessageResponse.of("Request rejected");
    }

    @Override
    @Transactional
    public DefaultMessageResponse leave(Gamer principal, UUID lobbyId) {
        Gamer gamer = requireGamer(principal.getUserId());
        Lobby lobby = requireVisibleLobby(lobbyId);
        if (lobby.isOwnedBy(gamer.getUserId())) {
            // The owner does not leave their own lobby; they cancel or end it.
            throw new BusinessException(TransactionCode.FORBIDDEN);
        }
        if (lobby.getStatus().finished()) {
            throw new BusinessException(TransactionCode.LOBBY_NOT_OPEN);
        }

        LobbyMember row = memberRepository
                .findByLobbyIdAndUserId(lobbyId, gamer.getUserId())
                .filter(m -> m.getStatus() == LobbyMemberStatus.ACCEPTED || m.getStatus() == LobbyMemberStatus.PENDING)
                .orElseThrow(() -> new BusinessException(TransactionCode.LOBBY_NOT_MEMBER));

        // Withdrawing a pending request and leaving the team are the same gesture.
        row.setStatus(LobbyMemberStatus.LEFT);
        memberRepository.save(row);
        fanOutToTeam(lobby, LobbyEvent.memberEvent(lobbyId.toString()));
        return DefaultMessageResponse.of("You left the lobby");
    }

    @Override
    @Transactional
    public DefaultMessageResponse kick(Gamer principal, UUID lobbyId, String userId) {
        Gamer owner = requireGamer(principal.getUserId());
        Lobby lobby = requireOwnedLobby(lobbyId, owner);
        if (lobby.getStatus().finished()) {
            throw new BusinessException(TransactionCode.LOBBY_NOT_OPEN);
        }

        LobbyMember row = memberRepository
                .findByLobbyIdAndUserId(lobbyId, userId)
                .filter(m -> m.getStatus() == LobbyMemberStatus.ACCEPTED)
                .orElseThrow(() -> new BusinessException(TransactionCode.LOBBY_NOT_MEMBER));

        row.setStatus(LobbyMemberStatus.KICKED);
        row.setDecidedAt(clock.instant());
        memberRepository.save(row);

        // The team sees the roster change; the kicked gamer's own screen updates too —
        // that is the one MEMBER frame that goes to somebody no longer on the roster.
        fanOutToTeam(lobby, LobbyEvent.memberEvent(lobbyId.toString()));
        gamerRepository
                .findById(userId)
                .ifPresent(kicked -> messaging.sendToUser(
                        kicked.getEmail(), "/queue/lobby", LobbyEvent.memberEvent(lobbyId.toString())));
        return DefaultMessageResponse.of("Member removed");
    }

    /**
     * Pins a lobby to the top of the browse list, for coins.
     *
     * <p>The replacement for the deck boost, and the reason it replaced it: a boosted deck
     * bought thirty minutes at the front of a stack of faces, and the buyer had no way to see
     * that anything had happened. A boosted lobby is a row at the top of a list with a frame
     * around it, which is visible to the person who paid for it as well as to everybody else.
     *
     * <p><b>It lasts until the lobby stops being open.</b> No expiry clock and nothing to
     * sweep: browse only ever shows OPEN lobbies, so locking, starting, ending or cancelling
     * ends the promotion by itself. That also makes the offer honest — what is bought is the
     * top of the list for the life of the plan, not a countdown that can run out while the
     * lobby is still filling.
     *
     * <p><b>No refund on cancel.</b> The boost delivered what it sold — visibility, from the
     * moment it was bought — and an owner who cancels has already had it.
     *
     * <p>Read-check-write inside one transaction, so the balance runs under the
     * {@code @Version} lock on {@link Gamer}: two taps would otherwise both read the old
     * balance and charge twice for one pin.
     */
    @Override
    @Transactional
    public LobbyResponse boost(Gamer principal, UUID lobbyId) {
        Gamer owner = requireGamer(principal.getUserId());
        Lobby lobby = requireOwnedLobby(lobbyId, owner);

        if (lobby.getStatus() != LobbyStatus.OPEN) {
            throw new BusinessException(TransactionCode.LOBBY_NOT_OPEN);
        }
        if (lobby.getBoostedAt() != null) {
            throw new BusinessException(TransactionCode.LOBBY_ALREADY_BOOSTED);
        }
        if (owner.getCoin() < LOBBY_BOOST_COST_COINS) {
            throw new BusinessException(TransactionCode.COIN_NOT_ENOUGH);
        }

        coins.spend(owner, LOBBY_BOOST_COST_COINS, CoinReason.LOBBY_BOOST);
        gamerRepository.save(owner);

        lobby.setBoostedAt(clock.instant());
        lobbyRepository.save(lobby);
        log.info("Gamer {} boosted lobby {} for {} coins", owner.getUserId(), lobbyId, LOBBY_BOOST_COST_COINS);

        return get(owner, lobbyId);
    }

    @Override
    @Transactional
    public DefaultMessageResponse lock(Gamer principal, UUID lobbyId) {
        Gamer owner = requireGamer(principal.getUserId());
        Lobby lobby = requireOwnedLobby(lobbyId, owner);
        if (lobby.getStatus() != LobbyStatus.OPEN) {
            throw new BusinessException(TransactionCode.LOBBY_NOT_OPEN);
        }

        // Locking answers the people still waiting, silently. They were not chosen; a
        // push saying so helps nobody, and their screen shows it.
        memberRepository
                .findAllByLobbyIdAndStatus(lobbyId, LobbyMemberStatus.PENDING)
                .forEach(pending -> {
                    pending.setStatus(LobbyMemberStatus.REJECTED);
                    pending.setDecidedAt(clock.instant());
                    memberRepository.save(pending);
                });

        lobby.setStatus(LobbyStatus.LOCKED);
        lobby.setLockedAt(clock.instant());
        lobbyRepository.save(lobby);
        fanOutToTeam(lobby, LobbyEvent.stateEvent(lobbyId.toString(), LobbyStatus.LOCKED));
        return DefaultMessageResponse.of("Lobby locked");
    }

    @Override
    @Transactional
    public DefaultMessageResponse unlock(Gamer principal, UUID lobbyId) {
        Gamer owner = requireGamer(principal.getUserId());
        Lobby lobby = requireOwnedLobby(lobbyId, owner);
        if (lobby.getStatus() != LobbyStatus.LOCKED) {
            throw new BusinessException(TransactionCode.LOBBY_NOT_OPEN);
        }

        lobby.setStatus(LobbyStatus.OPEN);
        lobby.setLockedAt(null);
        lobbyRepository.save(lobby);
        fanOutToTeam(lobby, LobbyEvent.stateEvent(lobbyId.toString(), LobbyStatus.OPEN));
        return DefaultMessageResponse.of("Lobby reopened");
    }

    @Override
    @Transactional
    public DefaultMessageResponse end(Gamer principal, UUID lobbyId) {
        Gamer owner = requireGamer(principal.getUserId());
        Lobby lobby = requireOwnedLobby(lobbyId, owner);
        if (lobby.getStatus() != LobbyStatus.LOCKED) {
            throw new BusinessException(TransactionCode.LOBBY_NOT_OPEN);
        }

        lobby.setStatus(LobbyStatus.ENDED);
        lobby.setEndedAt(clock.instant());
        lobbyRepository.save(lobby);
        fanOutToTeam(lobby, LobbyEvent.stateEvent(lobbyId.toString(), LobbyStatus.ENDED));
        return DefaultMessageResponse.of("Lobby ended");
    }

    @Override
    @Transactional
    public DefaultMessageResponse cancel(Gamer principal, UUID lobbyId) {
        Gamer owner = requireGamer(principal.getUserId());
        Lobby lobby = requireOwnedLobby(lobbyId, owner);
        // Only from OPEN. A LOCKED lobby is a formed team; the owner unlocks first, so a
        // cancel is always two deliberate gestures away from a team that exists.
        if (lobby.getStatus() != LobbyStatus.OPEN) {
            throw new BusinessException(TransactionCode.LOBBY_NOT_OPEN);
        }

        lobby.setStatus(LobbyStatus.CANCELLED);
        lobby.setEndedAt(clock.instant());
        lobbyRepository.save(lobby);

        notifyCancelled(lobby);
        fanOutToTeam(lobby, LobbyEvent.stateEvent(lobbyId.toString(), LobbyStatus.CANCELLED));
        return DefaultMessageResponse.of("Lobby cancelled");
    }

    // ------------------------------------------------------------------------

    /** Tells the accepted members, who were counting on it. Shared with the sweeper. */
    void notifyCancelled(Lobby lobby) {
        memberRepository
                .findAllByLobbyIdAndStatus(lobby.getId(), LobbyMemberStatus.ACCEPTED)
                .forEach(member -> gamerRepository
                        .findById(member.getUserId())
                        .ifPresent(gamer -> events.publishEvent(new NotificationRequestedEvent(
                                gamer.getUserId(),
                                gamer.getFcmToken(),
                                lobby.getTitle(),
                                "This lobby was called off",
                                NotificationKind.LOBBY_CANCELLED,
                                lobby.getId().toString()))));
    }

    /**
     * One frame to every socket on the team.
     *
     * <p>The principal is the <em>email</em> — {@code sendToUser} keys on the STOMP
     * principal, and delivering to a userId is a silent no-op. The resolve step is the
     * whole point of this method existing.
     */
    private void fanOutToTeam(Lobby lobby, LobbyEvent event) {
        List<String> teamIds = memberRepository.findAllByLobbyId(lobby.getId()).stream()
                .filter(m -> m.getStatus().inTeam())
                .map(LobbyMember::getUserId)
                .toList();
        gamerRepository
                .findAllById(teamIds)
                .forEach(gamer -> messaging.sendToUser(gamer.getEmail(), "/queue/lobby", event));
    }

    private Page<Lobby> browsePage(String gameId, LobbyTone tone, boolean startingSoon, Pageable pageable) {
        boolean byGame = gameId != null && !gameId.isBlank();
        Instant startsBefore = startingSoon ? clock.instant().plus(STARTING_SOON) : NO_START_BOUND;

        if (byGame && tone != null) {
            return lobbyRepository.browseByGameAndTone(LobbyStatus.OPEN, startsBefore, gameId, tone, pageable);
        }
        if (byGame) {
            return lobbyRepository.browseByGame(LobbyStatus.OPEN, startsBefore, gameId, pageable);
        }
        if (tone != null) {
            return lobbyRepository.browseByTone(LobbyStatus.OPEN, startsBefore, tone, pageable);
        }
        return lobbyRepository.browse(LobbyStatus.OPEN, startsBefore, pageable);
    }

    private LobbyDto toDto(Lobby lobby, Gamer owner, LobbyMemberStatus myStatus, long unread) {
        Games game = gamesRepository.findById(lobby.getGameId()).orElse(null);
        int playerCount = (int) memberRepository.countByLobbyIdAndStatusIn(lobby.getId(), TEAM);
        return mapper.toDto(lobby, owner, game, playerCount, myStatus, unread);
    }

    private long unreadCount(LobbyMember row) {
        Instant since = row.getLastReadAt() == null ? Instant.EPOCH : row.getLastReadAt();
        return messageRepository.countByLobbyIdAndCreatedAtAfter(row.getLobbyId(), since);
    }

    private Map<UUID, LobbyMemberStatus> myStatuses(String userId) {
        return memberRepository.findAllByUserIdAndStatusIn(userId, EnumSet.allOf(LobbyMemberStatus.class)).stream()
                .collect(Collectors.toMap(LobbyMember::getLobbyId, LobbyMember::getStatus));
    }

    private void requireJoinBudget(Gamer requester) {
        if (!lobbyJoinRateLimiter.tryAcquire(requester.getUserId())) {
            throw new BusinessException(TransactionCode.RATE_LIMITED);
        }
    }

    private LobbyMember requirePendingRequest(UUID lobbyId, String userId) {
        return memberRepository
                .findByLobbyIdAndUserId(lobbyId, userId)
                .filter(m -> m.getStatus() == LobbyMemberStatus.PENDING)
                .orElseThrow(() -> new BusinessException(TransactionCode.LOBBY_REQUEST_NOT_FOUND));
    }

    /** An archived lobby has left the app; answering 404 keeps that true everywhere. */
    private Lobby requireVisibleLobby(UUID lobbyId) {
        return lobbyRepository
                .findById(lobbyId)
                .filter(lobby -> lobby.getStatus() != LobbyStatus.ARCHIVED)
                .orElseThrow(() -> new BusinessException(TransactionCode.LOBBY_NOT_FOUND));
    }

    private Lobby requireOwnedLobby(UUID lobbyId, Gamer gamer) {
        Lobby lobby = requireVisibleLobby(lobbyId);
        if (!lobby.isOwnedBy(gamer.getUserId())) {
            throw new BusinessException(TransactionCode.FORBIDDEN);
        }
        return lobby;
    }

    private Instant requireReasonableStart(Instant startsAt) {
        Instant now = clock.instant();
        if (startsAt.isBefore(now.minus(STARTS_AT_PAST_GRACE))) {
            throw new BusinessException(TransactionCode.INVALID_REQUEST, "the planned start is in the past");
        }
        if (startsAt.isAfter(now.plus(STARTS_AT_HORIZON))) {
            throw new BusinessException(
                    TransactionCode.INVALID_REQUEST, "the planned start is more than two weeks away");
        }
        return startsAt;
    }

    /** PUBLIC surface: these fields face strangers in the browse feed. Null passes through. */
    private String screenPublic(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        TextAssessment assessment = textModeration.screen(text, TextSurface.PUBLIC);
        if (assessment.blocked()) {
            throw new BusinessException(TransactionCode.CONTENT_BLOCKED);
        }
        return assessment.cleaned();
    }

    private Gamer requireGamer(String userId) {
        return gamerRepository
                .findById(userId)
                .orElseThrow(() -> new BusinessException(TransactionCode.USER_NOT_FOUND));
    }

    private Map<String, Gamer> byId(List<Gamer> gamers) {
        return gamers.stream().collect(Collectors.toMap(Gamer::getUserId, Function.identity()));
    }

    private <T extends BaseModel, R extends BaseResponse<T>> R respond(R response, T body) {
        response.setBody(new BaseBody<>(body));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        return response;
    }
}
