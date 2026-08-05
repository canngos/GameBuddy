package com.gamebuddy.shared.repository;

import com.gamebuddy.shared.entity.Keywords;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Playstyle tags. Shared because both onboarding and the model's features read them. */
@Repository
public interface KeywordsRepository extends JpaRepository<Keywords, UUID> {}
