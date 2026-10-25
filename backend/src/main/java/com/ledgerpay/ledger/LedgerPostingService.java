package com.ledgerpay.ledger;

import com.ledgerpay.account.Account;
import com.ledgerpay.account.AccountRepository;
import com.ledgerpay.account.AccountStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class LedgerPostingService {

    private final LedgerTransactionRepository ledgerTransactionRepository;
    private final LedgerEntryService ledgerEntryService;
    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public LedgerPostingService(
            LedgerTransactionRepository ledgerTransactionRepository,
            LedgerEntryService ledgerEntryService,
            AccountRepository accountRepository,
            LedgerEntryRepository ledgerEntryRepository
    ) {
        this.ledgerTransactionRepository = ledgerTransactionRepository;
        this.ledgerEntryService = ledgerEntryService;
        this.accountRepository = accountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @Transactional
    public LedgerTransaction postTransfer(
            UUID sourceAccountId,
            UUID destinationAccountId,
            BigDecimal amount,
            String idempotencyKey
    ) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transfer amount must be greater than zero");
        }
        if (sourceAccountId == null || destinationAccountId == null) {
            throw new IllegalArgumentException("Source and destination account IDs are required");
        }
        if (sourceAccountId.equals(destinationAccountId)) {
            throw new IllegalArgumentException("Source and destination accounts must be different");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency key is required");
        }
        if (idempotencyKey.length() > 100) {
            throw new IllegalArgumentException("Idempotency key cannot exceed 100 characters");
        }

        Account sourceAccount = accountRepository.findById(sourceAccountId)
                .orElseThrow(() -> new IllegalArgumentException("Source account not found"));

        Account destinationAccount = accountRepository.findById(destinationAccountId)
                .orElseThrow(() -> new IllegalArgumentException("Destination account not found"));

        if (sourceAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalArgumentException("Source account is not active");
        }
        if (destinationAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalArgumentException("Destination account is not active");
        }

        if (!sourceAccount.getCurrency().equals(destinationAccount.getCurrency())) {
            throw new IllegalArgumentException("Source and destination account currencies must match");
        }

        int inserted = ledgerTransactionRepository.insertIfAbsent(idempotencyKey);

        if (inserted == 0) {
            throw new IllegalStateException("A transaction with this idempotency key already exists");
        }

        LedgerTransaction transaction = ledgerTransactionRepository
                .findByIdempotencyKey(idempotencyKey)
                .orElseThrow(
                    () -> new IllegalStateException("Transaction not found after insert")
        );

        ledgerEntryService.createEntry(
                transaction,
                sourceAccount,
                LedgerEntryType.DEBIT,
                amount
        );

        ledgerEntryService.createEntry(
                transaction,
                destinationAccount,
                LedgerEntryType.CREDIT,
                amount
        );

        transaction.markCompleted();
        return transaction;
    }
}
