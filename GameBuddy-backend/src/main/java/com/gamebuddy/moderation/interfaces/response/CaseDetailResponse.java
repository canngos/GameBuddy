package com.gamebuddy.moderation.interfaces.response;

import com.gamebuddy.common.base.BaseModel;
import com.gamebuddy.common.base.BaseResponse;
import com.gamebuddy.moderation.interfaces.dto.CaseDetailDto;
import lombok.Getter;
import lombok.Setter;

/** One case in full, with its evidence, for the case screen. */
public class CaseDetailResponse extends BaseResponse<CaseDetailResponse.Body> {

    @Getter
    @Setter
    public static class Body implements BaseModel {
        private CaseDetailDto caseDetail;
    }
}
