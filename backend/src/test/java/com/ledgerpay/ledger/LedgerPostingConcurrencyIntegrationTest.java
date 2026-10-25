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

            LedgerTransaction transaction1 = future1.get(10, TimeUnit.SECONDS);
            LedgerTransaction transaction2 = future2.get(10, TimeUnit.SECONDS);

            assertEquals(transaction1.getId(), transaction2.getId());

            LedgerTransaction savedTransaction =
                    ledgerTransactionRepository.findByIdempotencyKey(idempotencyKey)
                            .orElseThrow();

            assertEquals(transaction1.getId(), savedTransaction.getId());

            assertEquals(
                    LedgerTransactionStatus.COMPLETED,
                    savedTransaction.getStatus()
            );

            List<LedgerEntry> entries =
                    ledgerEntryRepository.findByTransactionId(savedTransaction.getId());

            assertEquals(2, entries.size());

            assertEquals(
                    0,
                    savedTransaction.getAmount().compareTo(new BigDecimal("100.00"))
            );

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