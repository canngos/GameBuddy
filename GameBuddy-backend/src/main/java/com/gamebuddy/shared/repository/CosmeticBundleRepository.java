package com.gamebuddy.shared.repository;

import com.gamebuddy.shared.entity.CosmeticBundle;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CosmeticBundleRepository extends JpaRepository<CosmeticBundle, UUID> {

    /**
     * The whole bundle shelf with its contents, in one query.
     *
     * <p>The entity graph is the point: without it this is a query for the bundles and then
     * one per bundle for its items, on a screen that shows all of them. The cosmetics store
     * is built in a fixed number of queries regardless of catalogue size and this keeps that
     * promise — see {@code DefaultCosmeticService.store}.
     */
    @EntityGraph(attributePaths = "items")
    List<CosmeticBundle> findAllByOrderBySortOrderAsc();

    @EntityGraph(attributePaths = "items")
    java.util.Optional<CosmeticBundle> findWithItemsById(UUID id);
}
