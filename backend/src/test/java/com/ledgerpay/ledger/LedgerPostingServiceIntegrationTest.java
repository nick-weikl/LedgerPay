package com.ledgerpay.ledger;

import com.ledgerpay.account.Account;
import com.ledgerpay.account.AccountBalanceService;
import com.ledgerpay.account.AccountRepository;
import com.ledgerpay.account.AccountStatus;
import com.ledgerpay.dto.AccountBalanceResponse;
import com.ledgerpay.exception.AccountNotFoundException;
import com.ledgerpay.exception.IdempotencyConflictException;
import com.ledgerpay.testutil.AccountFunding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import org.springframework.transaction.annotation.Propagation;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.time.Duration;

@SpringBootTest
@Transactional
class LedgerPostingServiceIntegrationTest {

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
    void shouldPostBalancedTransfer() {
        Account sourceAccount = accountRepository.save(
                new Account("Source Account", "USD", AccountStatus.ACTIVE)
        );

        Account destinationAccount = accountRepository.save(
                new Account("Destination Account", "USD", AccountStatus.ACTIVE)
        );

        Account fundingAccount = accountRepository.save(
                new Account("Test Funding Account", "USD", AccountStatus.ACTIVE)
        );

        accountFunding.fundAccount(
                fundingAccount,
                sourceAccount,
                new BigDecimal("500.00")
        );

        LedgerTransaction transaction = ledgerPostingService.postTransfer(
                sourceAccount.getId(),
                destinationAccount.getId(),
                new BigDecimal("100.00"),
                "transfer-" + UUID.randomUUID()
        );

        assertEquals(LedgerTransactionStatus.COMPLETED, transaction.getStatus());

        ledgerEntryRepository.flush();

        List<LedgerEntry> entries =
                ledgerEntryRepository.findByTransactionId(transaction.getId());

        assertEquals(2, entries.size());

        LedgerEntry debitEntry = entries.stream()
                .filter(entry -> entry.getEntryType() == LedgerEntryType.DEBIT)
                .findFirst()
                .orElseThrow();

        assertEquals(sourceAccount.getId(), debitEntry.getAccount().getId());
        assertEquals(new BigDecimal("100.00"), debitEntry.getAmount());

        LedgerEntry creditEntry = entries.stream()
                .filter(entry -> entry.getEntryType() == LedgerEntryType.CREDIT)
                .findFirst()
                .orElseThrow();

        assertEquals(destinationAccount.getId(), creditEntry.getAccount().getId());
        assertEquals(new BigDecimal("100.00"), creditEntry.getAmount());

        assertEquals(
                0,
                debitEntry.getAmount().compareTo(creditEntry.getAmount())
        );

        assertEquals(sourceAccount.getId(), transaction.getSourceAccountId());
        assertEquals(destinationAccount.getId(), transaction.getDestinationAccountId());
        assertEquals(new BigDecimal("100.00"), transaction.getAmount());
        assertEquals("USD",  transaction.getCurrency());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void shouldNotDuplicateTransferWithSameIdempotencyKey() {
        Account sourceAccount = accountRepository.save(
                new Account("Source Account", "USD", AccountStatus.ACTIVE)
        );

        Account destinationAccount = accountRepository.save(
                new Account("Destination Account", "USD", AccountStatus.ACTIVE)
        );

        Account fundingAccount = accountRepository.save(
                new Account("Test Funding Account", "USD", AccountStatus.ACTIVE)
        );

        LedgerTransaction fundingTransaction = accountFunding.fundAccount(
                fundingAccount,
                sourceAccount,
                new BigDecimal("500.00")
        );

        String idempotencyKey = "transfer-" + UUID.randomUUID();

        try {
            LedgerTransaction transaction = ledgerPostingService.postTransfer(
                    sourceAccount.getId(),
                    destinationAccount.getId(),
                    new BigDecimal("100.00"),
                    idempotencyKey
            );

            LedgerTransaction secondTransaction = ledgerPostingService.postTransfer(
                    sourceAccount.getId(),
                    destinationAccount.getId(),
                    new BigDecimal("100.00"),
                    idempotencyKey
            );

            assertEquals(transaction.getId(), secondTransaction.getId());

            List<LedgerEntry> entries =
                    ledgerEntryRepository.findByTransactionId(transaction.getId());

            assertEquals(2, entries.size());

            assertEquals(
                    LedgerTransactionStatus.COMPLETED,
                    ledgerTransactionRepository.findById(transaction.getId())
                            .orElseThrow()
                            .getStatus()
            );
        } finally {
            ledgerTransactionRepository.findByIdempotencyKey(idempotencyKey)
                    .ifPresent(transaction -> {
                        ledgerEntryRepository.deleteAll(
                                ledgerEntryRepository.findByTransactionId(transaction.getId())
                        );
                        ledgerTransactionRepository.delete(transaction);
                    });

            ledgerEntryRepository.deleteAll(
                    ledgerEntryRepository.findByTransactionId(fundingTransaction.getId())
            );

            ledgerTransactionRepository.deleteById(fundingTransaction.getId());

            ledgerEntryRepository.flush();
            ledgerTransactionRepository.flush();

            accountRepository.deleteById(sourceAccount.getId());
            accountRepository.deleteById(destinationAccount.getId());
            accountRepository.deleteById(fundingAccount.getId());
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void shouldRejectDifferentTransferWithSameIdempotencyKey() {
        Account sourceAccount = accountRepository.save(
                new Account("Source Account", "USD", AccountStatus.ACTIVE)
        );

        Account destinationAccount = accountRepository.save(
                new Account("Destination Account", "USD", AccountStatus.ACTIVE)
        );

        Account fundingAccount = accountRepository.save(
                new Account("Test Funding Account", "USD", AccountStatus.ACTIVE)
        );

        LedgerTransaction fundingTransaction = accountFunding.fundAccount(
                fundingAccount,
                sourceAccount,
                new BigDecimal("500.00")
        );

        String idempotencyKey = "transfer-" + UUID.randomUUID();

        try {
            LedgerTransaction transaction = ledgerPostingService.postTransfer(
                    sourceAccount.getId(),
                    destinationAccount.getId(),
                    new BigDecimal("100.00"),
                    idempotencyKey
            );

            assertThrows(
                    IdempotencyConflictException.class,
                    () -> ledgerPostingService.postTransfer(
                            sourceAccount.getId(),
                            destinationAccount.getId(),
                            new BigDecimal("250.00"),
                            idempotencyKey
                    )
            );
            assertEquals(LedgerTransactionStatus.COMPLETED, transaction.getStatus());
            LedgerTransaction savedTransaction = ledgerTransactionRepository
                    .findById(transaction.getId())
                    .orElseThrow();

            assertEquals(LedgerTransactionStatus.COMPLETED, savedTransaction.getStatus());

            assertEquals(
                    0,
                    savedTransaction.getAmount().compareTo(new BigDecimal("100.00"))
            );

            assertEquals(
                    2,
                    ledgerEntryRepository.findByTransactionId(transaction.getId()).size()
            );

        } finally {
            ledgerTransactionRepository.findByIdempotencyKey(idempotencyKey)
                    .ifPresent(transaction -> {
                        ledgerEntryRepository.deleteAll(
                                ledgerEntryRepository.findByTransactionId(transaction.getId())
                        );
                        ledgerTransactionRepository.delete(transaction);
                    });

            ledgerEntryRepository.deleteAll(
                    ledgerEntryRepository.findByTransactionId(fundingTransaction.getId())
            );

            ledgerTransactionRepository.deleteById(fundingTransaction.getId());

            ledgerEntryRepository.flush();
            ledgerTransactionRepository.flush();

            accountRepository.deleteById(sourceAccount.getId());
            accountRepository.deleteById(destinationAccount.getId());
            accountRepository.deleteById(fundingAccount.getId());
        }
    }

    @Test
    void shouldRejectTransferWhenSourceAccountDoesNotExist() {
        Account destinationAccount = accountRepository.save(
                new Account("Destination Account", "USD", AccountStatus.ACTIVE)
        );

        UUID missingSourceId = UUID.randomUUID();
        String idempotencyKey = "missing-source-test-" + UUID.randomUUID();

        AccountNotFoundException exception = assertThrows(
                AccountNotFoundException.class,
                () -> ledgerPostingService.postTransfer(
                        missingSourceId,
                        destinationAccount.getId(),
                        new BigDecimal("100.00"),
                        idempotencyKey
                )
        );

        assertEquals("Source account not found", exception.getMessage());

        assertTrue(
                ledgerTransactionRepository.findByIdempotencyKey(idempotencyKey).isEmpty()
        );
    }

    @Test
    void shouldRejectTransferWhenDestinationAccountDoesNotExist() {
        Account sourceAccount = accountRepository.save(
                new Account("Source Account", "USD", AccountStatus.ACTIVE)
        );

        UUID missingDestinationId = UUID.randomUUID();
        String idempotencyKey = "missing-destination-test-" + UUID.randomUUID();

        AccountNotFoundException exception = assertThrows(
                AccountNotFoundException.class,
                () -> ledgerPostingService.postTransfer(
                        sourceAccount.getId(),
                        missingDestinationId,
                        new BigDecimal("100.00"),
                        idempotencyKey
                )
        );

        assertEquals("Destination account not found", exception.getMessage());

        assertTrue(
                ledgerTransactionRepository.findByIdempotencyKey(idempotencyKey).isEmpty()
        );
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void shouldRejectTransferWithInsufficientFunds() {
        Account sourceAccount = accountRepository.save(
                new Account("Source Account", "USD", AccountStatus.ACTIVE)
        );

        Account destinationAccount = accountRepository.save(
                new Account("Destination Account", "USD", AccountStatus.ACTIVE)
        );

        String idempotencyKey = "insufficient-funds-test-" + UUID.randomUUID();

        try {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> ledgerPostingService.postTransfer(
                            sourceAccount.getId(),
                            destinationAccount.getId(),
                            new BigDecimal("100.00"),
                            idempotencyKey
                    )
            );

            assertEquals("Insufficient funds", exception.getMessage());

            assertTrue(
                    ledgerTransactionRepository.findByIdempotencyKey(idempotencyKey).isEmpty()
            );

        } finally {
            accountRepository.deleteById(sourceAccount.getId());
            accountRepository.deleteById(destinationAccount.getId());
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void shouldPreventConcurrentOverspending() throws Exception {

        Account sourceAccount = accountRepository.save(
                new Account("Concurrent Source", "USD", AccountStatus.ACTIVE)
        );

        Account destinationA = accountRepository.save(
                new Account("Destination A", "USD", AccountStatus.ACTIVE)
        );

        Account destinationB = accountRepository.save(
                new Account("Destination B", "USD", AccountStatus.ACTIVE)
        );

        Account fundingAccount = accountRepository.save(
                new Account("Concurrent Funding", "USD", AccountStatus.ACTIVE)
        );

        LedgerTransaction fundingTransaction = accountFunding.fundAccount(
                fundingAccount,
                sourceAccount,
                new BigDecimal("150.00")
        );

        String transferKeyA = "concurrent-A-" + UUID.randomUUID();
        String transferKeyB = "concurrent-B-" + UUID.randomUUID();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successfulTransfers = new AtomicInteger();
        AtomicInteger insufficientFundsFailures = new AtomicInteger();

        try {
            Callable<Void> transferA = () -> {
                startLatch.await();

                try {
                    ledgerPostingService.postTransfer(
                            sourceAccount.getId(),
                            destinationA.getId(),
                            new BigDecimal("100.00"),
                            transferKeyA
                    );

                    successfulTransfers.incrementAndGet();

                } catch (IllegalArgumentException e) {
                    if ("Insufficient funds".equals(e.getMessage())) {
                        insufficientFundsFailures.incrementAndGet();
                    } else {
                        throw e;
                    }
                }

                return null;
            };

            Callable<Void> transferB = () -> {
                startLatch.await();

                try {
                    ledgerPostingService.postTransfer(
                            sourceAccount.getId(),
                            destinationB.getId(),
                            new BigDecimal("100.00"),
                            transferKeyB
                    );

                    successfulTransfers.incrementAndGet();

                } catch (IllegalArgumentException e) {
                    if ("Insufficient funds".equals(e.getMessage())) {
                        insufficientFundsFailures.incrementAndGet();
                    } else {
                        throw e;
                    }
                }

                return null;
            };

            Future<Void> futureA = executor.submit(transferA);
            Future<Void> futureB = executor.submit(transferB);

            startLatch.countDown();

            futureA.get(15, TimeUnit.SECONDS);
            futureB.get(15, TimeUnit.SECONDS);

            assertEquals(1, successfulTransfers.get());
            assertEquals(1, insufficientFundsFailures.get());

            AccountBalanceResponse balanceResponse = accountBalanceService.calculateBalance(
                    sourceAccount.getId()
            );

            BigDecimal finalBalance = balanceResponse.balance();

            assertEquals(
                    0,
                    new BigDecimal("50.00").compareTo(finalBalance)
            );

        } finally {
            executor.shutdownNow();

            if (!executor.awaitTermination(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "Transfer threads did not terminate; cleanup would be unsafe"
                );
            }

            for (String key : List.of(transferKeyA, transferKeyB)) {
                ledgerTransactionRepository.findByIdempotencyKey(key)
                        .ifPresent(transaction -> {
                            ledgerEntryRepository.deleteAll(
                                    ledgerEntryRepository.findByTransactionId(
                                            transaction.getId()
                                    )
                            );

                            ledgerEntryRepository.flush();

                            ledgerTransactionRepository.deleteById(
                                    transaction.getId()
                            );

                            ledgerTransactionRepository.flush();
                        });
            }

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
            accountRepository.deleteById(destinationA.getId());
            accountRepository.deleteById(destinationB.getId());
            accountRepository.deleteById(fundingAccount.getId());
        }
    }
}