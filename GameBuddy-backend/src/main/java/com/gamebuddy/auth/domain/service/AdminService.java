package com.gamebuddy.auth.domain.service;

import com.gamebuddy.auth.interfaces.request.GameRequest;
import com.gamebuddy.auth.interfaces.request.KeywordRequest;
import com.gamebuddy.auth.interfaces.response.GamerResponse;
import com.gamebuddy.common.interfaces.DefaultMessageResponse;
import com.gamebuddy.shared.entity.Gamer;

public interface AdminService {

    GamerResponse getBlockedUsers(Gamer principal);

    DefaultMessageResponse banUser(Gamer principal, String userId);

    DefaultMessageResponse unbanUser(Gamer principal, String userId);

    DefaultMessageResponse addGame(Gamer principal, GameRequest gameRequest);

    DefaultMessageResponse addKeyword(Gamer principal, KeywordRequest keywordRequest);
}
