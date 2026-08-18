package com.gamebuddy.auth.interfaces.dto;

import com.gamebuddy.common.base.BaseModel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The ticket for step two, returned once and never retrievable again — only its hash is
 * kept. Deliberately not an access token: see {@code PasswordResetTicket}.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ResetVerifyResponseBody implements BaseModel {
    private String resetToken;
}
