package com.gamebuddy.shared.funnel;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FunnelEventRepository extends JpaRepository<FunnelEvent, UUID> {}
