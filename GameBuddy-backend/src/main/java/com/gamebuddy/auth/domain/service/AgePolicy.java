package com.gamebuddy.auth.domain.service;

import com.gamebuddy.common.enums.TransactionCode;
import com.gamebuddy.common.exception.BusinessException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;

/**
 * Who may hold an account.
 *
 * <p>GameBuddy is an adults-only service. That is a decision about what the app is, not
 * a rating chosen to satisfy a store: the product shows photographs of strangers, asks
 * for a yes or no, and opens a private conversation on a mutual yes. Whatever the
 * intent — and the intent here is finding people to play with — that mechanic is the one
 * every store and regulator reads as dating, and a service that mixes adults with
 * children inside it is indefensible however carefully the pools are separated.
 *
 * <p>The previous minimum was 12. It sat below the age of digital consent under GDPR
 * Article 8 in Finland (13) and below COPPA's threshold in the United States, and the
 * separation between minors and adults rested entirely on a number the account holder
 * could retype at any moment. {@link com.gamebuddy.common.enums.AgeBand} is kept even
 * though everyone is now an adult: it costs nothing and it still catches an account whose
 * age is missing or wrong.
 *
 * <p>A date of birth is asked for rather than an age. Both can be lied about — nothing
 * short of identity documents changes that — but a date of birth is checked here rather
 * than asserted by the client, does not go stale, and is the form a store reviewer
 * expects to see on the way in.
 */
public final class AgePolicy {

    /** Adults only. Also the line {@code AgeBand} draws. */
    public static final int MINIMUM_AGE = 18;

    /** Not a rule about people, a rule about typos and bad clients. */
    public static final int MAXIMUM_AGE = 99;

    private AgePolicy() {}

    /** Completed years between {@code birthDate} and today, in UTC. */
    public static int ageOn(LocalDate birthDate, Clock clock) {
        return Period.between(birthDate, LocalDate.now(clock.withZone(ZoneOffset.UTC)))
                .getYears();
    }

    /**
     * @return the age the date of birth implies
     * @throws BusinessException if the date is not one an account holder may have
     */
    public static int validate(LocalDate birthDate, Clock clock) {
        if (birthDate == null) {
            throw new BusinessException(TransactionCode.INVALID_REQUEST, "date of birth is required");
        }
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        if (birthDate.isAfter(today)) {
            throw new BusinessException(TransactionCode.INVALID_REQUEST, "date of birth is in the future");
        }
        int age = ageOn(birthDate, clock);
        if (age < MINIMUM_AGE) {
            throw new BusinessException(
                    TransactionCode.UNDERAGE, "you must be at least " + MINIMUM_AGE + " years old to use GameBuddy");
        }
        if (age > MAXIMUM_AGE) {
            throw new BusinessException(TransactionCode.INVALID_REQUEST, "that date of birth is not plausible");
        }
        return age;
    }
}
