package com.gamebuddy.profile.interfaces.dto;

import com.gamebuddy.common.base.BaseModel;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * A gamer's profile.
 *
 * <p>{@code email} and {@code coin} are populated only when a gamer is looking at their
 * own profile — see {@code DefaultProfileService#getUserInfo}. The endpoint used to
 * return them for any requested user id.
 */
@Getter
@Setter
public class UserInfoResponseBody implements BaseModel {
    private String userId;
    private String username;

    /** Own profile only. */
    private String email;

    private String age;

    /**
     * The date the age came from, {@code yyyy-MM-dd}, and only ever on your own profile.
     *
     * <p>Never sent for anybody else. An age is what other people are shown; a date of
     * birth narrows a stranger down to one of three hundred and sixty-five, which is more
     * than anyone needs to know about somebody they have not met.
     */
    private String birthDate;

    private String country;
    private String avatar;

    /** The worn frame and banner, as resolved URLs. Null for none, which is the default. */
    private String frame;

    private String banner;

    /**
     * The worn card theme's slug, or null for none.
     *
     * <p>A slug rather than colours: the client owns the palette so the contrast gate can
     * check it at build time. See {@link com.gamebuddy.shared.entity.CosmeticKind#THEME}.
     */
    private String theme;

    private String gender;

    /** Own profile only. */
    private Integer coin;

    /**
     * {@code USER} or {@code ADMIN}. Own profile only.
     *
     * <p>The client routes on this: a moderator gets the console instead of the deck, and
     * since the moderator account has no age, games or keywords, the onboarding check that
     * every other account passes would otherwise strand it on the "finish your profile"
     * screen forever.
     *
     * <p>Never sent for anyone else's profile. Which accounts are staff is not something a
     * response to a stranger should answer, and the only account it would identify is one
     * that is deliberately invisible.
     */
    private String role;

    private List<GamesDto> games;
    private List<KeywordsDto> keywords;

    /**
     * What this gamer plays on, as human-readable labels.
     *
     * <p>Public, like the games and keywords above: it is part of how somebody decides
     * whether they can actually play with this person, which is the whole question the
     * profile exists to answer.
     *
     * <p>Empty for accounts created before the field existed. The screen leaves the section
     * out rather than rendering an empty one — see {@code upgrade-2026-24-platforms.sql}
     * for why those are not backfilled with a guess.
     */
    private List<String> platforms;

    /**
     * The badges this gamer has chosen to display. Never more than three.
     *
     * <p>Was every earned achievement, as the JPA entity itself — a lazily-loaded
     * persistence object on the wire, so the JSON shape depended on Hibernate's proxy
     * state and any new column silently became a public API field. It is now a curated
     * three, which is also the only version of this that stays a fixed size as the
     * catalogue grows.
     */
    private List<ShowcasedBadgeDto> badges;

    /** How many badges have been earned in total. The number on the profile. */
    private Integer badgeCount;

    /** Own profile only. */
    private List<GamerDto> friends;

    /**
     * Verified Discord accounts, filtered to what this viewer may see.
     *
     * <p>Filtered rather than sent-and-hidden. Each link carries its own visibility, and a
     * client that received a restricted handle and was trusted to not draw it would be one
     * response inspection away from publishing it — the field is simply absent for a viewer
     * who has not matched with the owner.
     *
     * <p>Empty for the overwhelming majority of accounts, and the profile leaves the section
     * out entirely rather than rendering a heading over nothing.
     */
    private List<LinkedAccountDto> linkedAccounts;

    /**
     * Whether this account can sign in with an email and a password. Own profile only.
     *
     * <p>The settings screen needs it to choose between "Change password" and "Set a
     * password", and the delete-account card needs it to know whether to ask for one. An
     * account created through Google has no password at all — see
     * {@code DefaultSocialAuthService} for why that is stored honestly rather than as a
     * random hash nobody holds.
     */
    private Boolean hasPassword;

    /**
     * Which external identities can sign in as this account. Own profile only.
     *
     * <p>Enum names, {@code GOOGLE} and {@code DISCORD}. Deliberately unrelated to
     * {@link #linkedAccounts} above, which is a profile decoration other people can see:
     * these are credentials, they are never shown to anybody else, and the two lists can
     * legitimately disagree.
     */
    private List<String> authProviders;
}
