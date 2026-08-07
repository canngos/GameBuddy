package com.gamebuddy.auth.domain.service;

import static org.junit.jupiter.api.Assertions.*;

import com.gamebuddy.common.exception.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AgePolicy")
class AgePolicyTest {

    /** A Friday in August 2026. Every expectation below is relative to this date. */
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-07T12:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("someone comfortably over 18 is accepted, and the age is computed")
    void acceptsAnAdult() {
        assertEquals(28, AgePolicy.validate(LocalDate.of(1998, 1, 1), clock));
    }

    @Test
    @DisplayName("the birthday has not happened yet this year")
    void countsCompletedYearsOnly() {
        // Born 24 August 1998; on 7 August 2026 they are still 27.
        assertEquals(27, AgePolicy.validate(LocalDate.of(1998, 8, 24), clock));
    }

    @Test
    @DisplayName("eighteen today is old enough")
    void acceptsExactlyEighteenOnTheDay() {
        assertEquals(18, AgePolicy.validate(LocalDate.of(2008, 8, 7), clock));
    }

    @Test
    @DisplayName("eighteen tomorrow is not")
    void refusesTheDayBeforeTheirBirthday() {
        BusinessException ex =
                assertThrows(BusinessException.class, () -> AgePolicy.validate(LocalDate.of(2008, 8, 8), clock));
        assertEquals(168, ex.getTransactionCode().getId());
    }

    @Test
    void refusesAChild() {
        BusinessException ex =
                assertThrows(BusinessException.class, () -> AgePolicy.validate(LocalDate.of(2014, 3, 2), clock));
        assertEquals(168, ex.getTransactionCode().getId());
    }

    @Test
    @DisplayName("a date in the future is a mistake, not an age")
    void refusesTheFuture() {
        BusinessException ex =
                assertThrows(BusinessException.class, () -> AgePolicy.validate(LocalDate.of(2030, 1, 1), clock));
        assertEquals(148, ex.getTransactionCode().getId());
        assertTrue(ex.getMessage().contains("future"));
    }

    @Test
    void refusesAnImplausiblyOldDate() {
        assertThrows(BusinessException.class, () -> AgePolicy.validate(LocalDate.of(1850, 1, 1), clock));
    }

    @Test
    void refusesNull() {
        BusinessException ex = assertThrows(BusinessException.class, () -> AgePolicy.validate(null, clock));
        assertEquals(148, ex.getTransactionCode().getId());
    }

    @Test
    @DisplayName("29 February is handled by java.time, not by us")
    void handlesALeapDayBirthday() {
        // Born 29 February 2008. On 7 August 2026 they have had 18 birthdays, whatever a
        // non-leap year does about the day itself.
        assertEquals(18, AgePolicy.validate(LocalDate.of(2008, 2, 29), clock));
    }
}
