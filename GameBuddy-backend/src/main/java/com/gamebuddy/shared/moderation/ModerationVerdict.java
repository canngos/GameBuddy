package com.gamebuddy.shared.moderation;

/**
 * What should happen to an uploaded image.
 *
 * <p>Three outcomes rather than two, mirroring the classifier. Any single threshold is
 * wrong in one direction: set it low and ordinary photographs are refused, set it high and
 * the thing it exists to stop gets through. {@link #REVIEW} is the middle — the image is
 * stored, visible to nobody but its owner, and a person decides.
 *
 * <p>A caller that folds {@code REVIEW} into {@link #APPROVE} has removed the reason it
 * exists, and should not be written.
 */
public enum ModerationVerdict {
    APPROVE,
    REVIEW,
    REJECT
}
