package com.ledgerpay.ledger;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Service
public class LedgerTransactionService {

    private final LedgerTransactionRepository ledgerTransactionRepository;

    public LedgerTransactionService(LedgerTransactionRepository ledgerTransactionRepository) {
        this.ledgerTransactionRepository = ledgerTransactionRepository;
    }

    public Optional<LedgerTransaction> getTransactionByIdempotencyKey(String idempotencyKey) {
        return ledgerTransactionRepository.findByIdempotencyKey(idempotencyKey);
    }

    @Transactional
    public LedgerTransaction createTransaction(String idempotencyKey) {
        LedgerTransaction ledgerTransaction = new LedgerTransaction(
                idempotencyKey,
                LedgerTransactionStatus.PENDING
        );

        return ledgerTransactionRepository.save(ledgerTransaction);
    }

    public LedgerTransaction createTransactionIfAbsent(String idempotencyKey) {
        Optional<LedgerTransaction> transaction = getTransactionByIdempotencyKey(idempotencyKey);
        return transaction.orElseGet(() -> createTransaction(idempotencyKey));
    }

    @Transactional
    public LedgerTransaction createTransactionSafely(
            String idempotencyKey,
            UUID sourceAccountId,
            UUID destinationAccountId,
            BigDecimal amount,
            String currency
    ) {
        ledgerTransactionRepository.insertIfAbsent(
                idempotencyKey,
                sourceAccountId,
                destinationAccountId,
                amount,
                currency
        );

        Optional<LedgerTransaction> result =
                getTransactionByIdempotencyKey(idempotencyKey);

        return result.orElseThrow(() ->
                new IllegalStateException("Transaction not found after insert")
        );
    }
}