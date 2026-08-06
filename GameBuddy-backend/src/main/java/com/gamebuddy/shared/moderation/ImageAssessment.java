package com.gamebuddy.shared.moderation;

/**
 * What the classifier said, and how sure it was.
 *
 * <p>The score used to be logged and then dropped at this boundary, which cost two things
 * that only became obvious once there was a queue: a moderator could not see whether a
 * held image landed at 0.21 or 0.84, and the thresholds could never be retuned against
 * real traffic because the evidence was gone.
 *
 * <p>A null score is the important case. It does not mean "zero" — it means the classifier
 * never answered, so the image is held for a reason that has nothing to do with what is in
 * it. Those are worth re-screening automatically; a genuinely ambiguous score is not.
 *
 * @param verdict what to do with the image
 * @param score P(sexual content) from 0 to 1, or null when the classifier did not answer
 */
public record ImageAssessment(ModerationVerdict verdict, Double score) {

    /** The classifier was unreachable or unintelligible — an outage, not a judgement. */
    public boolean unscored() {
        return score == null;
    }

    static ImageAssessment held() {
        return new ImageAssessment(ModerationVerdict.REVIEW, null);
    }
}
