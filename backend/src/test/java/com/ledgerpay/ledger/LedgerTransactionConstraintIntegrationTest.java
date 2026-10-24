package com.ledgerpay.ledger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.UUID;

@SpringBootTest
public class LedgerTransactionConstraintIntegrationTest {

    @Autowired
    private LedgerTransactionRepository ledgerTransactionRepository;

    @Test
    void shouldRejectDuplicateIdempotencyKey() {
        String key = "payment-" + UUID.randomUUID();
        LedgerTransaction transaction1 = new LedgerTransaction(key, LedgerTransactionStatus.PENDING);
        LedgerTransaction transaction2 = new LedgerTransaction(key, LedgerTransactionStatus.PENDING);
        ledgerTransactionRepository.saveAndFlush(transaction1);
        assertThrows(
                DataIntegrityViolationException.class,
                () -> ledgerTransactionRepository.saveAndFlush(transaction2)
        );
    }
}
