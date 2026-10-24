package com.ledgerpay.ledger;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

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
}