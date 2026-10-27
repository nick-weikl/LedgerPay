package com.ledgerpay.testutil;

import com.ledgerpay.account.Account;
import com.ledgerpay.ledger.LedgerEntry;
import com.ledgerpay.ledger.LedgerEntryRepository;
import com.ledgerpay.ledger.LedgerEntryType;
import com.ledgerpay.ledger.LedgerTransaction;
import com.ledgerpay.ledger.LedgerTransactionRepository;
import com.ledgerpay.ledger.LedgerTransactionStatus;

import java.math.BigDecimal;
import java.util.UUID;

public class AccountFunding {
    private final LedgerTransactionRepository transactionRepository;
    private final LedgerEntryRepository entryRepository;

    public AccountFunding(LedgerTransactionRepository transactionRepository, LedgerEntryRepository entryRepository) {
        this.transactionRepository = transactionRepository;
        this.entryRepository = entryRepository;
    }

    public LedgerTransaction fundAccount(Account fundingAccount, Account targetAccount, BigDecimal amount) {
        LedgerTransaction transaction = new LedgerTransaction(
                "test-funding-" + UUID.randomUUID(),
                LedgerTransactionStatus.PENDING,
                fundingAccount.getId(),
                targetAccount.getId(),
                amount,
                "USD"
        );
        transaction = transactionRepository.save(transaction);
        entryRepository.save(new LedgerEntry(transaction, fundingAccount, LedgerEntryType.DEBIT, amount));
        entryRepository.save(new LedgerEntry(transaction, targetAccount, LedgerEntryType.CREDIT, amount));
        transaction.markCompleted();
        transactionRepository.saveAndFlush(transaction);
        entryRepository.flush();
        return transaction;
    }
}
