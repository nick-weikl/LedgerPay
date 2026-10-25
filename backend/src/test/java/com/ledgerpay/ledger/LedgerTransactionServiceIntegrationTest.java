package com.ledgerpay.ledger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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

    @Test
    void shouldCreateTransactionWhenIdempotencyKeyDoesNotExist() {
        String key = "missing-" + UUID.randomUUID();
        LedgerTransaction transaction = ledgerTransactionService.createTransactionIfAbsent(key);
        assertNotNull(transaction.getId());
        assertEquals(key, transaction.getIdempotencyKey());
        assertEquals(LedgerTransactionStatus.PENDING, transaction.getStatus());
    }

    @Test
    void shouldReturnExistingTransactionForDuplicateKey() {
        String key = "payment-" + UUID.randomUUID();
        LedgerTransaction transaction = ledgerTransactionService.createTransactionIfAbsent(key);
        LedgerTransaction transaction2 = ledgerTransactionService.createTransactionIfAbsent(key);
        assertEquals(transaction.getId(), transaction2.getId());
    }

    @Test
    void shouldSafelyCreateNewTransaction() {
        String key = "payment-" + UUID.randomUUID();
        UUID sourceAccountId = UUID.randomUUID();
        UUID destinationAccountId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("100.00");
        String currency = "USD";
        LedgerTransaction transaction = ledgerTransactionService.createTransactionSafely(
                key,
                sourceAccountId,
                destinationAccountId,
                amount,
                currency
        );
        assertNotNull(transaction.getId());
        assertEquals(LedgerTransactionStatus.PENDING, transaction.getStatus());
        assertEquals(key, transaction.getIdempotencyKey());
        assertEquals(sourceAccountId, transaction.getSourceAccountId());
        assertEquals(destinationAccountId, transaction.getDestinationAccountId());
        assertEquals(amount, transaction.getAmount());
        assertEquals(currency, transaction.getCurrency());
    }

    @Test
    void shouldReturnExistingTransactionSafely() {
        String key = "payment-" + UUID.randomUUID();
        UUID sourceAccountId = UUID.randomUUID();
        UUID destinationAccountId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("100.00");
        String currency = "USD";
        LedgerTransaction transaction = ledgerTransactionService.createTransactionSafely(
                key,
                sourceAccountId,
                destinationAccountId,
                amount,
                currency
        );
        LedgerTransaction transaction2 = ledgerTransactionService.createTransactionSafely(
                key,
                sourceAccountId,
                destinationAccountId,
                amount,
                currency
        );
        assertEquals(transaction.getId(), transaction2.getId());
        assertEquals(LedgerTransactionStatus.PENDING, transaction.getStatus());
        assertEquals(key, transaction.getIdempotencyKey());
        assertEquals(sourceAccountId, transaction2.getSourceAccountId());
        assertEquals(destinationAccountId, transaction2.getDestinationAccountId());
        assertEquals(amount, transaction.getAmount());
        assertEquals(currency, transaction.getCurrency());
    }
}