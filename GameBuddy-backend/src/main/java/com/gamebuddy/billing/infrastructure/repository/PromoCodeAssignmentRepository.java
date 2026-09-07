package com.gamebuddy.billing.infrastructure.repository;

import com.gamebuddy.billing.infrastructure.entity.PromoCodeAssignment;
import com.gamebuddy.billing.infrastructure.entity.PromoCodeAssignment.PromoAssignmentId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface PromoCodeAssignmentRepository extends JpaRepository<PromoCodeAssignment, PromoAssignmentId> {

    List<PromoCodeAssignment> findByIdCodeId(UUID codeId);

    List<PromoCodeAssignment> findByIdCodeIdAndEmailedAtIsNull(UUID codeId);

    boolean existsByIdCodeIdAndIdUserId(UUID codeId, String userId);

    long countByIdCodeId(UUID codeId);

    void deleteByIdCodeIdAndIdUserId(UUID codeId, String userId);

    /**
     * Addresses a code to an account, and leaves an existing assignment alone.
     *
     * <p>Native and idempotent for the same reason the redemption claim is: the key is
     * assigned, so {@code save()} would merge rather than insert — and here the damage
     * would be quieter than a double payout. Re-saving an assignment nulls
     * {@code emailedAt}, and the next send would post the same coupon to somebody who
     * already has it, once per edit of the code.
     *
     * @return 1 if this call created the assignment, 0 if it already existed
     */
    @Modifying
    @Query(value = """
                    INSERT INTO promo_code_assignment (code_id, user_id, created_at)
                    VALUES (:codeId, :userId, :createdAt)
                    ON CONFLICT (code_id, user_id) DO NOTHING
                    """, nativeQuery = true)
    int add(@Param("codeId") UUID codeId, @Param("userId") String userId, @Param("createdAt") Instant createdAt);

    /**
     * Records that the code reached this recipient.
     *
     * <p><b>The transaction is declared here rather than on the caller</b>, and that is not
     * a style choice. The send loop runs deliberately outside a transaction -- it talks to
     * an SMTP relay, and holding a write transaction open across that is how one slow relay
     * becomes a pool of stuck connections -- so a write called from it has nowhere to join.
     * Annotating the service method instead did not work at all: it is invoked from a
     * sibling method on the same bean, so the call never passes through the proxy that
     * would have started the transaction, and the first real send failed with
     * TransactionRequiredException. GamerRepository records the same lesson for
     * touchLastActive.
     *
     * <p>REQUIRES_NEW so each recipient is marked as it succeeds: a relay that dies halfway
     * through two hundred messages must leave the first hundred marked as sent, or the
     * retry posts them all again.
     */
    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query("""
            update PromoCodeAssignment a set a.emailedAt = :now
            where a.id.codeId = :codeId and a.id.userId = :userId
            """)
    int markEmailed(@Param("codeId") UUID codeId, @Param("userId") String userId, @Param("now") Instant now);

    /** Joins the transaction that created the assignment; see markEmailed. */
    @Modifying
    @Transactional
    @Query("""
            update PromoCodeAssignment a set a.notifiedAt = :now
            where a.id.codeId = :codeId and a.id.userId = :userId
            """)
    int markNotified(@Param("codeId") UUID codeId, @Param("userId") String userId, @Param("now") Instant now);
}
