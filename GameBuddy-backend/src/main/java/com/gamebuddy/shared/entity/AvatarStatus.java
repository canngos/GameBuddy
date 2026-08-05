package com.gamebuddy.shared.entity;

/**
 * Where an uploaded avatar is in review.
 *
 * <p>NULL — the column, not a constant here — means nothing has been uploaded and the
 * monogram stands in. There is deliberately no {@code NONE}: an enum constant would have
 * to be written to every row that has no avatar, which is most of them, to encode
 * information the null already carries.
 */
public enum AvatarStatus {

    /**
     * Screened as uncertain, or screened while the classifier was unreachable. Visible to
     * its owner only, and waiting on a person.
     */
    PENDING,

    /** Cleared. This is the only status anyone else sees. */
    APPROVED,

    /**
     * Refused. Kept rather than deleted immediately so a moderator can review an appeal
     * and so a pattern of uploads from one account is visible rather than invisible.
     */
    REJECTED
}
