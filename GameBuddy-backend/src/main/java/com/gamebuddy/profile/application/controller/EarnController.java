package com.gamebuddy.profile.application.controller;

import com.gamebuddy.common.base.BaseBody;
import com.gamebuddy.common.base.Status;
import com.gamebuddy.common.enums.TransactionCode;
import com.gamebuddy.common.exception.BusinessException;
import com.gamebuddy.profile.domain.coin.CoinEarningService;
import com.gamebuddy.profile.domain.coin.CoinEarningService.Earnings;
import com.gamebuddy.profile.domain.coin.CoinFaucet.Quest;
import com.gamebuddy.profile.interfaces.dto.EarnResponseBody;
import com.gamebuddy.profile.interfaces.dto.QuestDto;
import com.gamebuddy.profile.interfaces.response.EarnResponse;
import com.gamebuddy.shared.entity.Gamer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Ways to earn coins.
 *
 * <p>Every route answers with the same body — the whole earn screen — so a claim leaves the
 * client correct without a follow-up request, and the coin balance is never inferred by
 * adding a reward to a number that may already have moved.
 */
@RestController
@RequestMapping("/coins")
@RequiredArgsConstructor
public class EarnController {

    private final CoinEarningService earning;

    @GetMapping("/earn")
    public ResponseEntity<EarnResponse> earn(@AuthenticationPrincipal Gamer principal) {
        return ResponseEntity.ok(response(earning.state(principal)));
    }

    @PostMapping("/earn/daily")
    public ResponseEntity<EarnResponse> claimDaily(@AuthenticationPrincipal Gamer principal) {
        return ResponseEntity.ok(response(earning.claimDaily(principal)));
    }

    /**
     * @param code a {@link Quest} name. An unknown one is a 400 rather than a 500 —
     *     it is a client sending something we do not sell, not a server fault.
     */
    @PostMapping("/earn/quest/{code}")
    public ResponseEntity<EarnResponse> claimQuest(
            @AuthenticationPrincipal Gamer principal, @PathVariable String code) {
        return ResponseEntity.ok(response(earning.claimQuest(principal, quest(code))));
    }

    @PostMapping("/earn/stipend")
    public ResponseEntity<EarnResponse> claimStipend(@AuthenticationPrincipal Gamer principal) {
        return ResponseEntity.ok(response(earning.claimStipend(principal)));
    }

    private Quest quest(String code) {
        try {
            return Quest.valueOf(code.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(TransactionCode.INVALID_REQUEST, "unknown quest");
        }
    }

    private EarnResponse response(Earnings earnings) {
        EarnResponseBody body = new EarnResponseBody(
                earnings.dailyAvailable(),
                earnings.dailyReward(),
                earnings.streak(),
                earnings.dailyReadyAt(),
                earnings.quests().stream()
                        .map(q -> new QuestDto(
                                q.quest().name(),
                                q.quest().title(),
                                q.progress(),
                                q.quest().target(),
                                q.quest().reward(),
                                q.claimed()))
                        .toList(),
                earnings.stipendAvailable(),
                earnings.stipendAmount(),
                earnings.stipendReadyAt(),
                earnings.coinBalance(),
                earnings.adsLeftToday());

        EarnResponse response = new EarnResponse();
        response.setBody(new BaseBody<>(body));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        return response;
    }
}
