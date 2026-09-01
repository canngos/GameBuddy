package com.gamebuddy.billing.interfaces.dto;

import com.gamebuddy.common.base.BaseModel;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Every promotion code, newest first. */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PromoCodeListResponseBody implements BaseModel {

    private List<PromoCodeDto> codes;
}
