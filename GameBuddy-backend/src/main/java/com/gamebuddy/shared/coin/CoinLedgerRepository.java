package com.gamebuddy.shared.coin;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CoinLedgerRepository extends JpaRepository<CoinLedgerEntry, UUID> {}
