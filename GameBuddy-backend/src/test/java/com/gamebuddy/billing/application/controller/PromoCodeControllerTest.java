package com.gamebuddy.billing.application.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gamebuddy.billing.domain.PromoCodeService;
import com.gamebuddy.billing.interfaces.dto.MyPromoCodesResponseBody;
import com.gamebuddy.billing.interfaces.dto.RedeemPromoCodeResponseBody;
import com.gamebuddy.common.enums.TransactionCode;
import com.gamebuddy.common.exception.BusinessException;
import com.gamebuddy.common.exception.GlobalExceptionHandler;
import com.gamebuddy.common.security.JwtAuthenticationFilter;
import com.gamebuddy.shared.entity.Gamer;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(PromoCodeController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class PromoCodeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PromoCodeService promoCodeService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private AuthenticationProvider authenticationProvider;

    private Gamer gamer;

    @BeforeEach
    void setUp() {
        gamer = new Gamer();
        gamer.setUserId(UUID.randomUUID().toString());
        gamer.setEmail("player@example.com");
        gamer.setGamerUsername("player");
        gamer.setIsBlocked(false);

        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(gamer, null, gamer.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("the screen is told what is waiting for this account")
    void listsWaitingCodes() throws Exception {
        when(promoCodeService.mine(gamer.getUserId()))
                .thenReturn(new MyPromoCodesResponseBody(
                        List.of(new MyPromoCodesResponseBody.WaitingCode(
                                UUID.randomUUID().toString(),
                                "GIFT2026",
                                "COIN",
                                500,
                                null,
                                Instant.parse("2026-10-01T00:00:00Z"))),
                        List.of()));

        mockMvc.perform(get("/billing/promo-codes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.data.waiting[0].code").value("GIFT2026"))
                .andExpect(jsonPath("$.body.data.waiting[0].coinAmount").value(500));
    }

    @Test
    @DisplayName("redeeming answers with the balance the screen should show")
    void redeems() throws Exception {
        when(promoCodeService.redeem(anyString(), anyString()))
                .thenReturn(new RedeemPromoCodeResponseBody("COIN", 500, null, 600, null));

        mockMvc.perform(post("/billing/promo-codes/redeem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", "GIFT2026"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.data.kind").value("COIN"))
                .andExpect(jsonPath("$.body.data.coinBalance").value(600));
    }

    @Test
    @DisplayName("an empty code never reaches the service, so it does not spend a rate-limit permit")
    void rejectsABlankCode() throws Exception {
        mockMvc.perform(post("/billing/promo-codes/redeem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", "  "))))
                .andExpect(status().isBadRequest());

        verify(promoCodeService, never()).redeem(any(), any());
    }

    @Test
    @DisplayName("a code that does not exist answers 404 with the code the app translates")
    void reportsAnUnknownCode() throws Exception {
        when(promoCodeService.redeem(anyString(), anyString()))
                .thenThrow(new BusinessException(TransactionCode.PROMO_CODE_INVALID));

        mockMvc.perform(post("/billing/promo-codes/redeem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", "NOPE1234"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status.code").value("190"));
    }

    @Test
    @DisplayName("somebody else's gift answers 403, not 404")
    void reportsSomebodyElsesCode() throws Exception {
        when(promoCodeService.redeem(anyString(), anyString()))
                .thenThrow(new BusinessException(TransactionCode.PROMO_CODE_NOT_YOURS));

        mockMvc.perform(post("/billing/promo-codes/redeem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", "GIFT2026"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status.code").value("194"));
    }
}
