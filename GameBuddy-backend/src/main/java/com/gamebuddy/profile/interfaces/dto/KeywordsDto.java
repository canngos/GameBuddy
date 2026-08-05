package com.gamebuddy.profile.interfaces.dto;

import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KeywordsDto {
    private UUID id;
    private String keywordName;
    private String description;
}
