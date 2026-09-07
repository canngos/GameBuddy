package com.gamebuddy.billing.application.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gamebuddy.billing.domain.PromoCodeService;
import com.gamebuddy.billing.infrastructure.entity.PromoCode;
import com.gamebuddy.billing.infrastructure.entity.PromoCodeKind;
import com.gamebuddy.billing.interfaces.dto.PromoCodeDto;
import com.gamebuddy.common.enums.Role;
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

@WebMvcTest(PromoCodeAdminController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class PromoCodeAdminControllerTest {

    private static final UUID CODE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

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

    @BeforeEach
    void setUp() {
        Gamer admin = new Gamer();
        admin.setUserId(UUID.randomUUID().toString());
        admin.setEmail("admin@example.com");
        admin.setGamerUsername("admin");
        admin.setRole(Role.ADMIN);
        admin.setIsBlocked(false);

        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(admin, null, admin.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private PromoCodeDto dto() {
        return PromoCodeDto.builder()
                .id(CODE_ID.toString())
                .code("GIFT2026")
                .kind("COIN")
                .coinAmount(500)
                .expiresAt(Instant.parse("2026-10-01T00:00:00Z"))
                .status("ACTIVE")
                .build();
    }

    private Map<String, Object> body() {
        return Map.of("kind", "COIN", "coinAmount", 500, "validDays", 30);
    }

    @Test
    @DisplayName("the list is what the console draws its rows from")
    void listsCodes() throws Exception {
        when(promoCodeService.list()).thenReturn(List.of(dto()));

        mockMvc.perform(get("/admin/promo-codes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.data.codes[0].code").value("GIFT2026"))
                .andExpect(jsonPath("$.body.data.codes[0].status").value("ACTIVE"));
    }

    @Test
    @DisplayName("creating answers with the code, so it can be read out immediately")
    void createsACode() throws Exception {
        PromoCode created = new PromoCode();
        created.setId(CODE_ID);
        created.setCode("GIFT2026");
        created.setKind(PromoCodeKind.COIN);
        when(promoCodeService.create(any(), any())).thenReturn(created);
        when(promoCodeService.detail(eq(CODE_ID), any())).thenReturn(dto());

        mockMvc.perform(post("/admin/promo-codes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.data.code").value("GIFT2026"));
    }

    @Test
    @DisplayName("a request missing the kind never reaches the service")
    void rejectsAnIncompleteRequest() throws Exception {
        mockMvc.perform(post("/admin/promo-codes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("coinAmount", 500, "validDays", 30))))
                .andExpect(status().isBadRequest());

        verify(promoCodeService, never()).create(any(), any());
    }

    @Test
    @DisplayName("more than two hundred recipients in one call is refused before anything is sent")
    void rejectsTooManyRecipients() throws Exception {
        List<String> tooMany = java.util.stream.IntStream.range(0, 201)
                .mapToObj(Integer::toString)
                .toList();

        mockMvc.perform(post("/admin/promo-codes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("kind", "COIN", "coinAmount", 500, "validDays", 30, "assigneeIds", tooMany))))
                .andExpect(status().isBadRequest());

        verify(promoCodeService, never()).create(any(), any());
    }

    @Test
    @DisplayName("editing goes through the same body")
    void updatesACode() throws Exception {
        PromoCode updated = new PromoCode();
        updated.setId(CODE_ID);
        updated.setCode("GIFT2026");
        when(promoCodeService.update(eq(CODE_ID), any())).thenReturn(updated);
        when(promoCodeService.detail(eq(CODE_ID), any())).thenReturn(dto());

        mockMvc.perform(put("/admin/promo-codes/" + CODE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.data.code").value("GIFT2026"));
    }

    @Test
    @DisplayName("disabling is reversible and says so")
    void disablesAndEnables() throws Exception {
        PromoCode code = new PromoCode();
        code.setId(CODE_ID);
        code.setCode("GIFT2026");
        when(promoCodeService.setDisabled(CODE_ID, true)).thenReturn(code);
        when(promoCodeService.setDisabled(CODE_ID, false)).thenReturn(code);

        mockMvc.perform(post("/admin/promo-codes/" + CODE_ID + "/disable")).andExpect(status().isOk());
        mockMvc.perform(post("/admin/promo-codes/" + CODE_ID + "/enable")).andExpect(status().isOk());

        verify(promoCodeService).setDisabled(CODE_ID, true);
        verify(promoCodeService).setDisabled(CODE_ID, false);
    }

    @Test
    @DisplayName("deleting is a delete, not a second disable")
    void deletesACode() throws Exception {
        mockMvc.perform(delete("/admin/promo-codes/" + CODE_ID)).andExpect(status().isOk());

        verify(promoCodeService).delete(CODE_ID);
    }

    @Test
    @DisplayName("an unknown code answers 404 with the code the client branches on")
    void reportsAnUnknownCode() throws Exception {
        when(promoCodeService.get(CODE_ID)).thenThrow(new BusinessException(TransactionCode.PROMO_CODE_INVALID));

        mockMvc.perform(get("/admin/promo-codes/" + CODE_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status.code").value("190"));
    }
}
