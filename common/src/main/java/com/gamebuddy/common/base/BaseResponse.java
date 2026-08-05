package com.gamebuddy.common.base;

import lombok.Getter;
import lombok.Setter;

/**
 * Common shape of every REST response: a {@link Status} plus a typed {@link BaseBody}.
 *
 * @param <T> the concrete body payload type
 */
@Getter
@Setter
public abstract class BaseResponse<T extends BaseModel> {
    private Status status;
    private BaseBody<T> body;
}
