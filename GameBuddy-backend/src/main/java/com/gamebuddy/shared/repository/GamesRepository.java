package com.gamebuddy.shared.repository;

import com.gamebuddy.shared.entity.Games;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** The game catalogue. Read by profiles, recommendations and communities alike. */
@Repository
public interface GamesRepository extends JpaRepository<Games, String> {

    List<Games> findAllByIsPopularTrueOrderByAvgVoteDesc();
}
