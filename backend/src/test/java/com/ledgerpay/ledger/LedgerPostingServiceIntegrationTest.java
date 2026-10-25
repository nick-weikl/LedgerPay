package com.ledgerpay.ledger;

import com.ledgerpay.account.Account;
import com.ledgerpay.account.AccountRepository;
import com.ledgerpay.account.AccountStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import org.springframework.transaction.annotation.Propagation;

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

    @Test
    void shouldPostBalancedTransfer() {
        Account sourceAccount = accountRepository.save(
                new Account("Source Account", "USD", AccountStatus.ACTIVE)
        );

        Account destinationAccount = accountRepository.save(
                new Account("Destination Account", "USD", AccountStatus.ACTIVE)
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

        String idempotencyKey = "transfer-" + UUID.randomUUID();

        try {
            LedgerTransaction transaction = ledgerPostingService.postTransfer(
                    sourceAccount.getId(),
                    destinationAccount.getId(),
                    new BigDecimal("100.00"),
                    idempotencyKey
            );

            assertThrows(
                    IllegalStateException.class,
                    () -> ledgerPostingService.postTransfer(
                            sourceAccount.getId(),
                            destinationAccount.getId(),
                            new BigDecimal("100.00"),
                            idempotencyKey
                    )
            );

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

            accountRepository.deleteById(sourceAccount.getId());
            accountRepository.deleteById(destinationAccount.getId());
        }
    }
}