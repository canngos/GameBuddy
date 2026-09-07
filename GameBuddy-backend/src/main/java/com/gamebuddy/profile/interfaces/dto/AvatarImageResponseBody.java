package com.gamebuddy.profile.interfaces.dto;

import com.gamebuddy.common.base.BaseModel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One image under review, inlined as a {@code data:} URI.
 *
 * <p>JSON rather than raw {@code image/jpeg} bytes, which is what this endpoint served
 * first. The bytes are in the private bucket, so the client has to authenticate — and a
 * React Native {@code <Image>} silently drops the Authorization header on Android, so the
 * request arrived unauthenticated and the moderator saw a blank square with a 401 visible
 * only in the server log. Fetching it as JSON puts the image on exactly the same
 * authenticated path as every other request the app makes.
 *
 * <p>Base64 costs a third more bytes. That is the right trade here — these are normalised
 * avatars of a few tens of kilobytes and the queue is capped — and would be the wrong one
 * for full-size media.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AvatarImageResponseBody implements BaseModel {

    /** A complete {@code data:image/jpeg;base64,...} URI, ready to render. */
    private String image;
}
