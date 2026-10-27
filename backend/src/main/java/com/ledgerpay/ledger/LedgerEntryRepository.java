package com.ledgerpay.ledger;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {
    List<LedgerEntry> findByTransactionId(UUID transactionId);
    long countByAccountId(UUID accountId);

    @Query("""
        SELECT COALESCE(SUM(e.amount), 0)
        FROM LedgerEntry e
        WHERE e.entryType = com.ledgerpay.ledger.LedgerEntryType.CREDIT
        AND e.account.id = :accountId
        """)
    BigDecimal sumCreditsByAccountId(@Param("accountId") UUID accountId);

    @Query("""
        SELECT COALESCE(SUM(e.amount), 0)
        FROM LedgerEntry e
        WHERE e.entryType = com.ledgerpay.ledger.LedgerEntryType.DEBIT
        AND e.account.id = :accountId
        """)
    BigDecimal sumDebitsByAccountId(@Param("accountId") UUID accountId);
}
