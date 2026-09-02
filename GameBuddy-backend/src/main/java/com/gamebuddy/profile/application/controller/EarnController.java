package com.gamebuddy.profile.application.controller;

import com.gamebuddy.common.base.BaseBody;
import com.gamebuddy.common.base.Status;
import com.gamebuddy.common.enums.TransactionCode;
import com.gamebuddy.profile.domain.coin.CoinEarningService;
import com.gamebuddy.profile.domain.coin.CoinEarningService.Earnings;
import com.gamebuddy.profile.domain.mission.Mission;
import com.gamebuddy.profile.domain.mission.MissionService;
import com.gamebuddy.profile.interfaces.dto.EarnResponseBody;
import com.gamebuddy.profile.interfaces.dto.MissionDto;
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
 *
 * <p>That contract is what makes the mission rotation work at all: finishing the third of
 * three deals the next three, and they come back in the response to the claim rather than
 * in a refetch the client would have to know to make.
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
     * @param code a {@code Mission.code}. Unknown, or not one of this gamer's three, is a
     *     400 rather than a 500 — it is a client asking for something we are not offering,
     *     which is a request problem and not a server fault. The service decides; this does
     *     not pre-validate, because "is it one of yours" is not a question a path variable
     *     can answer.
     */
    @PostMapping("/earn/mission/{code}")
    public ResponseEntity<EarnResponse> claimMission(
            @AuthenticationPrincipal Gamer principal, @PathVariable String code) {
        return ResponseEntity.ok(response(earning.claimMission(principal, code)));
    }

    @PostMapping("/earn/stipend")
    public ResponseEntity<EarnResponse> claimStipend(@AuthenticationPrincipal Gamer principal) {
        return ResponseEntity.ok(response(earning.claimStipend(principal)));
    }

    private EarnResponse response(Earnings earnings) {
        MissionService.ActiveSet set = earnings.missions();

        EarnResponseBody body = new EarnResponseBody(
                earnings.dailyAvailable(),
                earnings.dailyReward(),
                earnings.streak(),
                earnings.dailyReadyAt(),
                set.missions().stream()
                        .map(m -> new MissionDto(
                                m.mission().getCode(),
                                m.mission().getTitle(),
                                m.slot(),
                                m.progress(),
                                m.mission().getTarget(),
                                m.reward(),
                                m.claimed()))
                        .toList(),
                set.setIndex(),
                Mission.SETS,
                set.band().name(),
                set.veteran(),
                earnings.stipendAvailable(),
                earnings.stipendAmount(),
                earnings.stipendReadyAt(),
                earnings.coinBalance(),
                earnings.adsLeftToday(),
                earnings.dailyLadder(),
                earnings.adCoins());

        EarnResponse response = new EarnResponse();
        response.setBody(new BaseBody<>(body));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        return response;
    }
}
