package com.ledgerpay.ledger;

import com.ledgerpay.account.Account;
import com.ledgerpay.account.AccountRepository;
import com.ledgerpay.account.AccountStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@Transactional
class LedgerEntryServiceIntegrationTest {

    @Autowired
    private LedgerEntryService ledgerEntryService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private LedgerTransactionRepository ledgerTransactionRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Test
    void shouldCreateLedgerEntry() {
        String idempotencyKey = "payment-" + UUID.randomUUID();

        LedgerTransaction ledgerTransaction = new LedgerTransaction(
                idempotencyKey,
                LedgerTransactionStatus.PENDING
        );

        ledgerTransaction = ledgerTransactionRepository.save(ledgerTransaction);

        Account account = new Account(
                "Test Account",
                "USD",
                AccountStatus.ACTIVE
        );
        account =  accountRepository.save(account);

        LedgerEntry entry = ledgerEntryService.createEntry(
                ledgerTransaction,
                account,
                LedgerEntryType.DEBIT,
                new BigDecimal("100.00")
        );

        assertNotNull(entry.getId());
        assertEquals(LedgerEntryType.DEBIT, entry.getEntryType());
        assertEquals(new BigDecimal("100.00"), entry.getAmount());
        assertEquals(account.getId(), entry.getAccount().getId());
        assertEquals(ledgerTransaction.getId(), entry.getTransaction().getId());

        ledgerEntryRepository.flush();

        LedgerEntry savedEntry = ledgerEntryRepository
                .findById(entry.getId())
                .orElseThrow();

        assertEquals(entry.getId(), savedEntry.getId());
    }
}