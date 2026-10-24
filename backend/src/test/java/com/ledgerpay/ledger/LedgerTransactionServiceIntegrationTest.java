package com.ledgerpay.ledger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class LedgerTransactionServiceIntegrationTest {

    @Autowired
    private LedgerTransactionService ledgerTransactionService;

    @Autowired
    private LedgerTransactionRepository ledgerTransactionRepository;

    @Test
    void shouldReturnEmptyWhenIdempotencyKeyDoesNotExist() {
        String idempotencyKey = "missing-" + UUID.randomUUID();

        Optional<LedgerTransaction> result = ledgerTransactionService.getTransactionByIdempotencyKey(idempotencyKey);
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldFindTransactionByIdempotencyKey() {
        String key = "payment-" + UUID.randomUUID();

        LedgerTransaction transaction = new LedgerTransaction(key, LedgerTransactionStatus.COMPLETED);
        ledgerTransactionRepository.save(transaction);
        Optional<LedgerTransaction> result = ledgerTransactionService.getTransactionByIdempotencyKey(key);
        assertTrue(result.isPresent());
        assertEquals(key, result.get().getIdempotencyKey());
    }

    @Test
    void shouldCreatePendingTransaction() {
        String key = "payment-" + UUID.randomUUID();

        LedgerTransaction transaction = ledgerTransactionService.createTransaction(key);
        assertNotNull(transaction.getId());
        assertEquals(LedgerTransactionStatus.PENDING, transaction.getStatus());
        assertEquals(key, transaction.getIdempotencyKey());
    }
}