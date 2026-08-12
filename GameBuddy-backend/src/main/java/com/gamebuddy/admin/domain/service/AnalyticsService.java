package com.gamebuddy.admin.domain.service;

import com.gamebuddy.admin.infrastructure.repository.AnalyticsRepository;
import com.gamebuddy.admin.interfaces.dto.AnalyticsResponseBody;
import com.gamebuddy.admin.interfaces.dto.AnalyticsResponseBody.DailyPoint;
import com.gamebuddy.admin.interfaces.response.AnalyticsResponse;
import com.gamebuddy.common.base.BaseBody;
import com.gamebuddy.common.base.Status;
import com.gamebuddy.common.enums.TransactionCode;
import com.gamebuddy.community.domain.service.ModerationService;
import com.gamebuddy.match.domain.service.chat.ChatMessageService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The numbers behind the console's analytics screen.
 *
 * <p>Reads only. This module owns no tables and writes nothing: everything here is a
 * question about data another module is responsible for, which is why the two counts it
 * cannot answer from the shared {@code gamer} table are asked of the owning module's
 * service rather than of its repository.
 *
 * <p>Deliberately not cached. At launch scale these are three cheap aggregates against one
 * operator's dashboard; a cache would add a staleness question to a screen whose whole
 * purpose is to say what is true right now. It wants revisiting when the table is large
 * enough that the full scan shows.
 */
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    /** How much history the growth graph carries. Long enough to see a trend, short
     * enough to stay readable on a phone. */
    private static final int GROWTH_WINDOW_DAYS = 30;

    private final AnalyticsRepository analytics;
    private final ModerationService moderation;
    private final ChatMessageService chat;
    private final Clock clock;

    @Transactional(readOnly = true)
    public AnalyticsResponse snapshot() {
        Instant now = clock.instant();
        var counts = analytics.headline(
                now, now.minus(Duration.ofDays(1)), now.minus(Duration.ofDays(7)), now.minus(Duration.ofDays(30)));

        AnalyticsResponseBody body = new AnalyticsResponseBody();
        body.setAccounts(counts.getAccounts());
        body.setRegistered(counts.getRegistered());
        body.setActiveToday(counts.getActiveToday());
        body.setActiveWeek(counts.getActiveWeek());
        body.setActiveMonth(counts.getActiveMonth());
        body.setNewToday(counts.getNewToday());
        body.setNewWeek(counts.getNewWeek());
        body.setBanned(counts.getBanned());
        body.setDeleted(counts.getDeleted());
        body.setSubscribers(counts.getSubscribers());
        body.setMinors(counts.getMinors());
        body.setAvatarsPending(counts.getAvatarsPending());

        body.setOpenReports(moderation.openReportCount());
        body.setOldestOpenReportHours(moderation.oldestOpenReportHours());
        body.setMessages(chat.messageCount());
        body.setMutualMatches(analytics.countMutualMatches());
        body.setGrowth(growth(now));

        // A thirty-day window on the funnel, matching the longest headline count. Short
        // enough that a change in the product shows up, long enough that the denominators
        // are not single digits.
        Instant since = now.minus(Duration.ofDays(30));
        var f = analytics.funnel(since, now.minus(Duration.ofDays(30)), now.minus(Duration.ofDays(60)));
        body.setFunnel(new AnalyticsResponseBody.Funnel(
                f.getPaywallViewers(),
                f.getCheckoutStarters(),
                f.getTrialsStarted(),
                f.getPaidStarted(),
                f.getRenewals(),
                f.getCohort30(),
                f.getCohort30Paid(),
                f.getCoinsEarned(),
                f.getCoinsSpent()));

        // Only accounts that have had seven days to come back. Including younger ones would
        // report every cohort as sinking, because a signup from yesterday cannot yet have
        // returned a week later.
        body.setRetention(
                analytics.retentionByCohort(now.minus(Duration.ofDays(90)), now.minus(Duration.ofDays(7))).stream()
                        .map(r -> new AnalyticsResponseBody.CohortRetention(
                                r.getCohort(), r.getSignups(), r.getRetained()))
                        .toList());

        AnalyticsResponse response = new AnalyticsResponse();
        response.setBody(new BaseBody<>(body));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        return response;
    }

    /**
     * The growth series, with every day present.
     *
     * <p>The query returns only days that had a signup. Handing those straight to a chart
     * draws a flat line through the quiet days and compresses the busy ones — the gaps have
     * to be zeros, or the shape of the graph is a lie about when the growth happened.
     */
    private List<DailyPoint> growth(Instant now) {
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        LocalDate from = today.minusDays(GROWTH_WINDOW_DAYS - 1L);

        Map<LocalDate, Long> byDay = new HashMap<>();
        analytics
                .signupsSince(from.atStartOfDay(ZoneOffset.UTC).toInstant())
                .forEach(row -> byDay.put(row.getDay(), row.getSignups()));

        List<DailyPoint> series = new ArrayList<>(GROWTH_WINDOW_DAYS);
        long running = 0;
        for (LocalDate day = from; !day.isAfter(today); day = day.plusDays(1)) {
            long signups = byDay.getOrDefault(day, 0L);
            running += signups;
            series.add(new DailyPoint(day, signups, running));
        }
        return series;
    }
}
