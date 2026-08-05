package com.gamebuddy.profile.interfaces.dto;

import com.gamebuddy.common.base.BaseModel;
import lombok.Getter;
import lombok.Setter;

/**
 * The result of uploading an avatar.
 *
 * <p>{@code status} is here because the client must be able to tell three outcomes apart,
 * and a bare success would collapse them. APPROVED means it is live; PENDING means a
 * person will look and it is visible to nobody else meanwhile; REJECTED means it was
 * refused. Showing "uploaded!" for all three would be untrue in two of them.
 *
 * <p>{@code url} is returned in every case, including the two where nobody else can see
 * the image, so the owner can see what they uploaded while being told it is under review.
 */
@Getter
@Setter
public class AvatarUploadResponseBody implements BaseModel {

    private String status;
    private String url;
}
