package com.gamebuddy.match.interfaces.dto;

import com.gamebuddy.common.base.BaseModel;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class RecommendationResponseBody implements BaseModel {

    private List<GamerDto> recommendedGamers;
}
