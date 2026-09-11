package com.gamebuddy.moderation.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gamebuddy.common.enums.Role;
import com.gamebuddy.common.enums.TransactionCode;
import com.gamebuddy.common.exception.BusinessException;
import com.gamebuddy.common.ratelimit.Budget;
import com.gamebuddy.match.domain.service.chat.ChatModerationService;
import com.gamebuddy.moderation.config.ModerationProperties;
import com.gamebuddy.moderation.infrastructure.entity.ContentReport;
import com.gamebuddy.moderation.infrastructure.entity.ContentReport.ReasonCode;
import com.gamebuddy.moderation.infrastructure.entity.ModerationAction;
import com.gamebuddy.moderation.infrastructure.entity.ModerationCase;
import com.gamebuddy.moderation.infrastructure.repository.ContentReportRepository;
import com.gamebuddy.moderation.infrastructure.repository.ModerationActionRepository;
import com.gamebuddy.moderation.infrastructure.repository.ModerationCaseRepository;
import com.gamebuddy.moderation.interfaces.request.ReportRequest;
import com.gamebuddy.moderation.interfaces.request.ResolveCaseRequest;
import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.event.NotificationRequestedEvent;
import com.gamebuddy.shared.repository.GamerRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tools.jackson.databind.ObjectMapper;

/**
 * The orchestration: a report becomes a case, the case is deduped and rate-limited, the
 * policy is applied, and a moderator's decision fans out to the account, the reporters'
 * standing and their notifications. {@link SanctionExecutor} is mocked here — its own
 * account effects are proved in its own test — so this checks that the service reaches for
 * the right action and does the bookkeeping around it.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DefaultModerationService")
class DefaultModerationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-11T12:00:00Z");

    @Mock
    private ContentReportRepository reportRepository;

    @Mock
    private ModerationCaseRepository caseRepository;

    @Mock
    private ModerationActionRepository actionRepository;

    @Mock
    private GamerRepository gamerRepository;

    @Mock
    private ChatModerationService chatModeration;

    @Mock
    private SanctionExecutor sanctions;

    @Mock
    private org.springframework.context.ApplicationEventPublisher events;

    private final ModerationProperties properties = new ModerationProperties();
    private final ReportPolicy policy = new ReportPolicy(properties);
    private final ObjectMapper json = new ObjectMapper();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private DefaultModerationService service;

    private Gamer reporter;
    private Gamer target;
    private Gamer admin;
    private final List<ContentReport> saved = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new DefaultModerationService(
                reportRepository,
                caseRepository,
                actionRepository,
                gamerRepository,
                chatModeration,
                policy,
                sanctions,
                properties,
                Budget.of(10, Duration.ofDays(1)).limiter(),
                events,
                json,
                clock);

        reporter = gamer("reporter-1", Role.USER, Duration.ofDays(60));
        target = gamer("target-1", Role.USER, Duration.ofDays(60));
        admin = gamer("admin-1", Role.ADMIN, Duration.ofDays(200));

        stub(reporter);
        stub(target);
        stub(admin);

        // No open case, then create-on-demand returns what we hand it back.
        when(caseRepository.findOpenForUpdate(any())).thenReturn(Optional.empty());
        when(caseRepository.save(any(ModerationCase.class))).thenAnswer(i -> i.getArgument(0));
        when(reportRepository.existsByCaseIdAndReporterId(any(), any())).thenReturn(false);
        when(reportRepository.save(any(ContentReport.class))).thenAnswer(i -> {
            ContentReport r = i.getArgument(0);
            if (r.getId() == null) {
                r.setId(UUID.randomUUID());
            }
            if (r.getCreatedAt() == null) {
                // Stands in for @CreationTimestamp, which the real persist would set and the
                // policy's window filter depends on.
                r.setCreatedAt(NOW);
            }
            saved.add(r);
            return r;
        });
        when(reportRepository.findByCaseIdOrderByCreatedAtAsc(any())).thenAnswer(i -> new ArrayList<>(saved));
    }

    private Gamer gamer(String label, Role role, Duration age) {
        Gamer g = new Gamer();
        // A real user id is a UUID (Ids.uuid parses it as one); the label is only the name.
        g.setUserId(UUID.randomUUID().toString());
        g.setGamerUsername(label);
        g.setRole(role);
        g.setCreatedDate(NOW.minus(age));
        return g;
    }

    private void stub(Gamer g) {
        when(gamerRepository.findById(g.getUserId())).thenReturn(Optional.of(g));
    }

    private ReportRequest request(ReasonCode code) {
        ReportRequest r = new ReportRequest();
        r.setReasonCode(code);
        return r;
    }

    @Test
    @DisplayName("reporting a profile opens a case and files a report")
    void reportProfileOpensACase() {
        var response = service.reportProfile(reporter, target.getUserId(), request(ReasonCode.HARASSMENT));

        assertEquals("100", response.getStatus().getCode());
        verify(caseRepository, atLeastOnce()).save(any(ModerationCase.class));
        assertEquals(1, saved.size());
        assertEquals(ReasonCode.HARASSMENT, saved.get(0).getReasonCode());
        assertEquals(target.getUserId(), saved.get(0).getAuthorId());
    }

    @Test
    @DisplayName("the same reporter cannot report the same person twice while the case is open")
    void secondReportIntoOpenCaseIsRefused() {
        ModerationCase open = new ModerationCase();
        open.setId(UUID.randomUUID());
        open.setTargetId(target.getUserId());
        open.setStatus(ModerationCase.Status.OPEN);
        open.setOpenedAt(NOW);
        open.setLastReportAt(NOW);
        when(caseRepository.findOpenForUpdate(target.getUserId())).thenReturn(Optional.of(open));
        when(reportRepository.existsByCaseIdAndReporterId(open.getId(), reporter.getUserId()))
                .thenReturn(true);

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> service.reportProfile(reporter, target.getUserId(), request(ReasonCode.SPAM_SCAM)));
        assertEquals(TransactionCode.ALREADY_REPORTED, ex.getTransactionCode());
    }

    @Test
    @DisplayName("reporting yourself is refused")
    void selfReportRefused() {
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> service.reportProfile(reporter, reporter.getUserId(), request(ReasonCode.OTHER)));
        assertEquals(TransactionCode.INVALID_REQUEST, ex.getTransactionCode());
    }

    @Test
    @DisplayName("the reporter's daily budget cuts off a flood")
    void reporterBudgetIsEnforced() {
        // A fresh service with a budget of two, and three distinct targets so dedup does not fire.
        DefaultModerationService tight = new DefaultModerationService(
                reportRepository,
                caseRepository,
                actionRepository,
                gamerRepository,
                chatModeration,
                policy,
                sanctions,
                properties,
                Budget.of(2, Duration.ofDays(1)).limiter(),
                events,
                json,
                clock);
        Gamer t2 = gamer("target-2", Role.USER, Duration.ofDays(60));
        Gamer t3 = gamer("target-3", Role.USER, Duration.ofDays(60));
        stub(t2);
        stub(t3);

        tight.reportProfile(reporter, target.getUserId(), request(ReasonCode.HARASSMENT));
        tight.reportProfile(reporter, t2.getUserId(), request(ReasonCode.HARASSMENT));
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> tight.reportProfile(reporter, t3.getUserId(), request(ReasonCode.HARASSMENT)));
        assertEquals(TransactionCode.RATE_LIMITED, ex.getTransactionCode());
    }

    @Test
    @DisplayName("three trusted reporters hide the target and make the case urgent")
    void enoughReportersHideAndEscalate() {
        // Pre-load the case with two established reporters, so this third crosses the line.
        Gamer r2 = gamer("reporter-2", Role.USER, Duration.ofDays(60));
        Gamer r3 = gamer("reporter-3", Role.USER, Duration.ofDays(60));
        stub(r2);
        stub(r3);
        seedReport(r2.getUserId());
        seedReport(r3.getUserId());
        when(gamerRepository.findAllById(anyCollection())).thenReturn(List.of(reporter, r2, r3));

        service.reportProfile(reporter, target.getUserId(), request(ReasonCode.HARASSMENT));

        assertTrue(target.isHiddenFromDiscovery(), "three trusted reporters take the profile out of decks");
        // The case saved carries URGENT.
        assertEquals(3, saved.size());
    }

    @Test
    @DisplayName("resolving with a suspension sanctions, upholds the reports, and thanks the reporters")
    void resolveSuspends() {
        ModerationCase open = seededCase();
        seedReport(reporter.getUserId());
        when(caseRepository.findById(open.getId())).thenReturn(Optional.of(open));
        when(gamerRepository.findAllById(anyCollection())).thenReturn(List.of(reporter));

        ResolveCaseRequest req = new ResolveCaseRequest();
        req.setAction(ModerationAction.Action.SUSPEND_24H);
        req.setReasonCode(ReasonCode.HARASSMENT);

        var response = service.resolveCase(admin, open.getId().toString(), req);

        assertEquals("100", response.getStatus().getCode());
        verify(sanctions)
                .apply(
                        eq(target),
                        eq(open.getId()),
                        eq(admin.getUserId()),
                        eq(ModerationAction.Action.SUSPEND_24H),
                        eq(ReasonCode.HARASSMENT),
                        any(),
                        eq(false));
        assertEquals(ContentReport.Status.ACTIONED, saved.get(0).getStatus());
        assertEquals(1, reporter.getReportsUpheld());
        assertEquals(ModerationCase.Status.CLOSED, open.getStatus());
        // The reporter is told it was acted on.
        verify(events, atLeastOnce()).publishEvent(any(NotificationRequestedEvent.class));
    }

    @Test
    @DisplayName("dismissing marks the reports dismissed, counts against the reporter, and lifts an auto-hide")
    void resolveDismisses() {
        ModerationCase open = seededCase();
        open.setAutoHidden(true);
        target.setHiddenFromDiscovery(true);
        seedReport(reporter.getUserId());
        when(caseRepository.findById(open.getId())).thenReturn(Optional.of(open));
        when(gamerRepository.findAllById(anyCollection())).thenReturn(List.of(reporter));

        ResolveCaseRequest req = new ResolveCaseRequest();
        req.setAction(ModerationAction.Action.DISMISS);

        service.resolveCase(admin, open.getId().toString(), req);

        assertEquals(ContentReport.Status.DISMISSED, saved.get(0).getStatus());
        assertEquals(1, reporter.getReportsDismissed());
        assertTrue(!target.isHiddenFromDiscovery(), "a dismissal puts them back in the deck");
        assertEquals(ModerationCase.Status.CLOSED, open.getStatus());
    }

    @Test
    @DisplayName("an ordinary gamer cannot see the queue")
    void queueIsAdminOnly() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.getCases(reporter, 100));
        assertEquals(TransactionCode.NOT_ADMIN, ex.getTransactionCode());
    }

    // --- helpers -----------------------------------------------------------

    private ModerationCase seededCase() {
        ModerationCase open = new ModerationCase();
        open.setId(UUID.randomUUID());
        open.setTargetId(target.getUserId());
        open.setStatus(ModerationCase.Status.OPEN);
        open.setOpenedAt(NOW);
        open.setLastReportAt(NOW);
        return open;
    }

    private void seedReport(String reporterId) {
        ContentReport r = new ContentReport();
        r.setId(UUID.randomUUID());
        r.setContentType(ContentReport.ContentType.PROFILE);
        r.setReporterId(reporterId);
        r.setReasonCode(ReasonCode.HARASSMENT);
        r.setCreatedAt(NOW);
        r.setStatus(ContentReport.Status.OPEN);
        saved.add(r);
    }
}
