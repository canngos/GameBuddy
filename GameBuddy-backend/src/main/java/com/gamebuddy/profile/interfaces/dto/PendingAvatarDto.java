package com.gamebuddy.profile.interfaces.dto;

import java.time.Instant;

/**
 * One upload awaiting a verdict.
 *
 * <p>No image URL. The bytes sit in the private bucket and have no public URL by design, so
 * the console fetches them from {@code GET /admin/avatars/{userId}/image} with its own
 * credentials. Returning a signed link here would put a working, unauthenticated URL to an
 * unscreened image into a response body, which is the two-bucket split undone.
 *
 * @param userId whose avatar this is — also the key for approving or rejecting it
 * @param username so the moderator can see who they are judging
 * @param uploadedAt when the upload arrived, so the queue can show an honest wait time
 * @param score what the classifier scored it, or null if the classifier never answered —
 *     which tells the moderator whether this is a borderline image or merely one that
 *     arrived while the model was down and has not been re-screened yet
 */
public record PendingAvatarDto(String userId, String username, Instant uploadedAt, Double score) {}
