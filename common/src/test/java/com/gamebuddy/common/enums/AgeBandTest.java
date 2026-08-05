package com.gamebuddy.common.enums;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * This decides who may be put in a private one-to-one chat with whom, so it gets the
 * boundary cases explicitly.
 */
class AgeBandTest {

    @ParameterizedTest
    @ValueSource(ints = {12, 13, 16, 17})
    void testOf_whenUnderMajority_ReturnsMinor(int age) {
        assertEquals(AgeBand.MINOR, AgeBand.of(age));
    }

    @ParameterizedTest
    @ValueSource(ints = {18, 19, 40, 99})
    void testOf_whenAtOrOverMajority_ReturnsAdult(int age) {
        assertEquals(AgeBand.ADULT, AgeBand.of(age));
    }

    @Test
    @DisplayName("an unknown age is treated as a minor, never as an adult")
    void testOf_whenAgeIsNull_ReturnsMinor() {
        // Failing open here would put anyone with an unset age into the adult pool.
        assertEquals(AgeBand.MINOR, AgeBand.of(null));
    }

    @ParameterizedTest
    @CsvSource({"17, 18", "18, 17", "12, 99", "99, 12", "17, 99"})
    @DisplayName("a minor and an adult are never compatible, in either direction")
    void testCompatible_whenBandsDiffer_ReturnsFalse(int left, int right) {
        assertFalse(AgeBand.compatible(left, right));
    }

    @ParameterizedTest
    @CsvSource({"12, 17", "17, 12", "18, 99", "99, 18", "25, 26"})
    void testCompatible_whenBandsMatch_ReturnsTrue(int left, int right) {
        assertTrue(AgeBand.compatible(left, right));
    }

    @Test
    @DisplayName("the boundary is exactly 18: 17 and 18 are not compatible")
    void testCompatible_atTheBoundary() {
        assertTrue(AgeBand.compatible(17, 17));
        assertTrue(AgeBand.compatible(18, 18));
        assertFalse(AgeBand.compatible(17, 18));
    }

    @Test
    void testCompatible_whenEitherAgeIsNull_TreatsBothAsMinors() {
        assertTrue(AgeBand.compatible(null, 15));
        assertFalse(AgeBand.compatible(null, 30));
    }
}
