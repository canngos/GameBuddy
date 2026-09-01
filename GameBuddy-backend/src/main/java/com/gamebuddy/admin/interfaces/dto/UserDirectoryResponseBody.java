package com.gamebuddy.admin.interfaces.dto;

import com.gamebuddy.common.base.BaseModel;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One page of accounts, for choosing who to send something to.
 *
 * <p>Carries the total as well as the page, unlike the rest of the console's lists. The
 * screen offers "select everybody who matches", and offering that without saying how many
 * that is would be asking somebody to sign for an unopened box.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UserDirectoryResponseBody implements BaseModel {

    private List<DirectoryUser> users;
    private int page;
    private int totalPages;
    private long total;

    /**
     * Enough to recognise somebody and no more.
     *
     * <p>The address is here because two similar usernames are only reliably told apart by
     * it. What is deliberately absent is everything a directory would be tempted to carry:
     * no device token, no country, no age, no coin balance. This exists to answer "is this
     * the person I mean", not to be browsed.
     *
     * @param lastActiveAt null for an account that has never done anything
     * @param gold membership as it stands right now, expiry included — not the stored tier,
     *     which outlives the membership it describes
     */
    public record DirectoryUser(
            String userId,
            String username,
            String email,
            String avatar,
            Instant createdDate,
            Instant lastActiveAt,
            boolean gold) {}
}
