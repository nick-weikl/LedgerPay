package com.ledgerpay.ledger;

import com.ledgerpay.account.Account;
import com.ledgerpay.account.AccountRepository;
import com.ledgerpay.account.AccountStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class LedgerPostingConcurrencyIntegrationTest {

    @Autowired
    private LedgerPostingService ledgerPostingService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private LedgerTransactionRepository ledgerTransactionRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Test
    void shouldPreventConcurrentDuplicateTransfers() throws Exception {
        Account sourceAccount = accountRepository.save(
                new Account("Rollback Source", "USD", AccountStatus.ACTIVE)
        );

        Account destinationAccount = accountRepository.save(
                new Account("Rollback Destination", "USD", AccountStatus.ACTIVE)
        );

        String idempotencyKey = "transfer-" + UUID.randomUUID();

        ExecutorService executorService = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch countDownLatch = new CountDownLatch(1);

            Callable<LedgerTransaction> transferTask = () -> {
                countDownLatch.await();

                return ledgerPostingService.postTransfer(
                        sourceAccount.getId(),
                        destinationAccount.getId(),
                        new BigDecimal("100.00"),
                        idempotencyKey
                );
            };

            Future<LedgerTransaction> future1 = executorService.submit(transferTask);
            Future<LedgerTransaction> future2 = executorService.submit(transferTask);

            countDownLatch.countDown();

            int successCount = 0;
            int duplicateCount = 0;

            for (Future<LedgerTransaction> future : List.of(future1, future2)) {
                try {
                    future.get(10, TimeUnit.SECONDS);
                    successCount++;

                } catch (ExecutionException exception) {
                    Throwable cause = exception.getCause();

                    if (cause instanceof IllegalStateException
                            && "A transaction with this idempotency key already exists"
                            .equals(cause.getMessage())) {
                        duplicateCount++;
                    } else {
                        throw new AssertionError("Unexpected transfer failure", cause);
                    }
                }
            }

            assertEquals(1,  successCount);
            assertEquals(1,  duplicateCount);

            LedgerTransaction savedTransaction =
                    ledgerTransactionRepository.findByIdempotencyKey(idempotencyKey)
                            .orElseThrow();

            assertEquals(
                    LedgerTransactionStatus.COMPLETED,
                    savedTransaction.getStatus()
            );

            List<LedgerEntry> entries =
                    ledgerEntryRepository.findByTransactionId(savedTransaction.getId());

            assertEquals(2, entries.size());

        } finally {
            executorService.shutdown();

            if (!executorService.awaitTermination(15, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }

            ledgerTransactionRepository.findByIdempotencyKey(idempotencyKey)
                    .ifPresent(transaction -> {
                        ledgerEntryRepository.deleteAll(
                                ledgerEntryRepository.findByTransactionId(transaction.getId())
                        );
                        ledgerTransactionRepository.delete(transaction);
                    });

            accountRepository.deleteById(sourceAccount.getId());
            accountRepository.deleteById(destinationAccount.getId());
        }
    }
}