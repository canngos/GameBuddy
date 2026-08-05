package com.gamebuddy.match.infrastructure.entity;

/** Why a candidate appeared on someone's screen. */
public enum ImpressionSource {

    /** Chosen by the recommendation model because it ranked them similar. */
    MODEL,

    /**
     * Chosen at random, from the exploration slots on the page.
     *
     * <p>Worth separating because these are the only impressions not conditioned on the
     * model already believing in the pairing. Fitting the desirability prior on MODEL
     * impressions alone would measure the model's own past opinions rather than whether
     * gamers actually like each other.
     */
    EXPLORATION
}
