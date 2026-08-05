package com.gamebuddy.common.interfaces;

import com.gamebuddy.common.base.BaseModel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Body for endpoints that only need to acknowledge success with a message. */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class DefaultMessageBody implements BaseModel {
    private String message;
}
