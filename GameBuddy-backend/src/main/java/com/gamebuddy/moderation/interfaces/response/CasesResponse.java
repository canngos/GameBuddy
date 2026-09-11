package com.gamebuddy.moderation.interfaces.response;

import com.gamebuddy.common.base.BaseModel;
import com.gamebuddy.common.base.BaseResponse;
import com.gamebuddy.moderation.interfaces.dto.CaseSummaryDto;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** The moderation queue: open and urgent cases, urgent first. */
public class CasesResponse extends BaseResponse<CasesResponse.Body> {

    @Getter
    @Setter
    public static class Body implements BaseModel {
        private List<CaseSummaryDto> cases;
    }
}
