package com.gamebuddy.auth.interfaces.dto;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GamerDto {

    private String userId;
    private String username;
    private String email;
    private String avatar;
    private Instant createdDate;
}
