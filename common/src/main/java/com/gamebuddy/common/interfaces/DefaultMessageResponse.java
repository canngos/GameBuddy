package com.gamebuddy.common.interfaces;

import com.gamebuddy.common.base.BaseBody;
import com.gamebuddy.common.base.BaseResponse;
import com.gamebuddy.common.base.Status;
import com.gamebuddy.common.enums.TransactionCode;

/** Response used by every endpoint whose only payload is a confirmation message. */
public class DefaultMessageResponse extends BaseResponse<DefaultMessageBody> {

    /** Convenience factory for the overwhelmingly common "succeeded, here's a message" case. */
    public static DefaultMessageResponse of(String message) {
        DefaultMessageResponse response = new DefaultMessageResponse();
        response.setBody(new BaseBody<>(new DefaultMessageBody(message)));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        return response;
    }
}
