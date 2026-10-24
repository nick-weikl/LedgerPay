package com.ledgerpay.ledger;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, UUID> {
    Optional<LedgerTransaction> findByIdempotencyKey(String idempotencyKey);

    @Modifying
    @Query(
            value = """
        INSERT INTO ledger_transactions (
            idempotency_key,
            status
        )
        VALUES (
            :idempotencyKey,
            'PENDING'
        )
        ON CONFLICT (idempotency_key) DO NOTHING
        """,
            nativeQuery = true
    )
    int insertIfAbsent(@Param("idempotencyKey") String idempotencyKey);
}
