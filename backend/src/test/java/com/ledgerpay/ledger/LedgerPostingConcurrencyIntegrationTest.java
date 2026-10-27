package com.ledgerpay.ledger;

import com.ledgerpay.account.Account;
import com.ledgerpay.account.AccountBalanceService;
import com.ledgerpay.account.AccountRepository;
import com.ledgerpay.account.AccountStatus;
import com.ledgerpay.testutil.AccountFunding;
import org.junit.jupiter.api.BeforeEach;
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

    private AccountFunding accountFunding;

    @BeforeEach
    void setUpAccountFunding() {
        accountFunding = new AccountFunding(ledgerTransactionRepository, ledgerEntryRepository);
    }

    @Autowired
    private AccountBalanceService accountBalanceService;

    @Test
    void shouldPreventConcurrentDuplicateTransfers() throws Exception {
        Account sourceAccount = accountRepository.save(
                new Account("Rollback Source", "USD", AccountStatus.ACTIVE)
        );

        Account destinationAccount = accountRepository.save(
                new Account("Rollback Destination", "USD", AccountStatus.ACTIVE)
        );

        Account fundingAccount = accountRepository.save(
                new Account("Concurrency Test Funding", "USD", AccountStatus.ACTIVE)
        );

        LedgerTransaction fundingTransaction = accountFunding.fundAccount(
                fundingAccount,
                sourceAccount,
                new BigDecimal("150.00")
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

            BigDecimal remainingBalance = accountBalanceService
                    .calculateBalance(sourceAccount.getId())
                    .balance();

            assertEquals(
                    0,
                    new BigDecimal("50.00").compareTo(remainingBalance)
            );

        } finally {
            executorService.shutdownNow();

            if (!executorService.awaitTermination(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "Transfer threads did not terminate; cleanup would be unsafe"
                );
            }

            ledgerTransactionRepository.findByIdempotencyKey(idempotencyKey)
                    .ifPresent(transaction -> {
                        ledgerEntryRepository.deleteAll(
                                ledgerEntryRepository.findByTransactionId(transaction.getId())
                        );
                        ledgerTransactionRepository.delete(transaction);
                    });

            ledgerEntryRepository.deleteAll(
                    ledgerEntryRepository.findByTransactionId(
                            fundingTransaction.getId()
                    )
            );
            ledgerEntryRepository.flush();

            ledgerTransactionRepository.deleteById(
                    fundingTransaction.getId()
            );
            ledgerTransactionRepository.flush();

            accountRepository.deleteById(sourceAccount.getId());
            accountRepository.deleteById(destinationAccount.getId());
            accountRepository.deleteById(fundingAccount.getId());
        }
    }

}