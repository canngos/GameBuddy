package com.gamebuddy.auth.interfaces.response;

import com.gamebuddy.auth.interfaces.dto.TokenResponseBody;
import com.gamebuddy.common.base.BaseResponse;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TokenResponse extends BaseResponse<TokenResponseBody> {}
