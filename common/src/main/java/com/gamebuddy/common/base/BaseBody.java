package com.gamebuddy.common.base;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Envelope holding the typed payload of a response.
 *
 * @param <T> the concrete {@link BaseModel} carried by this response
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class BaseBody<T extends BaseModel> {
    private T data;
}
