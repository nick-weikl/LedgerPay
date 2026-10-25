package com.ledgerpay.ledger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class LedgerTransactionConcurrencyIntegrationTest {

    @Autowired
    private LedgerTransactionService ledgerTransactionService;

    @Autowired
    private LedgerTransactionRepository ledgerTransactionRepository;

    @Test
    void shouldHandleConcurrentRequestsWithSameIdempotencyKey() throws Exception {
        String idempotencyKey = "payment-" + UUID.randomUUID();
        UUID sourceAccountId = UUID.randomUUID();
        UUID destinationAccountId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("100.00");
        String currency = "USD";
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch countDownLatch = new CountDownLatch(1);

            Future<LedgerTransaction> firstResult = executorService.submit(() -> {
                countDownLatch.await();
                return ledgerTransactionService.createTransactionSafely(
                        idempotencyKey,
                        sourceAccountId,
                        destinationAccountId,
                        amount,
                        currency
                );
            });

            Future<LedgerTransaction> secondResult = executorService.submit(() -> {
                countDownLatch.await();
                return ledgerTransactionService.createTransactionSafely(
                        idempotencyKey,
                        sourceAccountId,
                        destinationAccountId,
                        amount,
                        currency
                );
            });

            countDownLatch.countDown();
            LedgerTransaction transaction1 = firstResult.get(10, TimeUnit.SECONDS);
            LedgerTransaction transaction2 = secondResult.get(10, TimeUnit.SECONDS);
            assertEquals(transaction1.getId(), transaction2.getId());
            LedgerTransaction savedTransaction = ledgerTransactionRepository
                    .findByIdempotencyKey(idempotencyKey)
                    .orElseThrow();

            assertEquals(transaction1.getId(), savedTransaction.getId());
        } finally {
            executorService.shutdownNow();

            if (executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                ledgerTransactionRepository.findByIdempotencyKey(idempotencyKey)
                        .ifPresent(ledgerTransactionRepository::delete);
            }
        }
    }
}
