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
    private String country;
    private String avatar;

    /** The worn frame and banner, as resolved URLs. Null for none, which is the default. */
    private String frame;

    private String banner;

    private String gender;

    /** Own profile only. */
    private Integer coin;

    private List<GamesDto> games;
    private List<KeywordsDto> keywords;

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

    private List<CommunityDto> joinedCommunities;

    /** Own profile only. */
    private List<GamerDto> friends;
}
