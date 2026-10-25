package com.ledgerpay.ledger;

import com.ledgerpay.account.Account;
import com.ledgerpay.account.AccountRepository;
import com.ledgerpay.account.AccountStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class LedgerPostingRollbackIntegrationTest {

    @Autowired
    private LedgerPostingService ledgerPostingService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private LedgerTransactionRepository ledgerTransactionRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @MockitoSpyBean
    private LedgerEntryService ledgerEntryService;

    @Test
    void shouldRollbackWhenCreditEntryFails() {
        Account sourceAccount = accountRepository.save(
                new Account("Rollback Source", "USD", AccountStatus.ACTIVE)
        );

        Account destinationAccount = accountRepository.save(
                new Account("Rollback Destination", "USD", AccountStatus.ACTIVE)
        );

        try {
            doAnswer(invocation -> {
                LedgerEntryType entryType = invocation.getArgument(2);

                if (entryType == LedgerEntryType.CREDIT) {
                    throw new RuntimeException("Simulated credit entry failure");
                }

                return invocation.callRealMethod();
            }).when(ledgerEntryService).createEntry(
                    any(LedgerTransaction.class),
                    any(Account.class),
                    any(LedgerEntryType.class),
                    any(BigDecimal.class)
            );

            String idempotencyKey = "rollback-" + UUID.randomUUID();

            RuntimeException exception = assertThrows(
                    RuntimeException.class,
                    () -> ledgerPostingService.postTransfer(
                            sourceAccount.getId(),
                            destinationAccount.getId(),
                            new BigDecimal("100.00"),
                            idempotencyKey
                    )
            );

            assertTrue(
                    ledgerTransactionRepository
                            .findByIdempotencyKey(idempotencyKey)
                            .isEmpty()
            );

            assertEquals("Simulated credit entry failure", exception.getMessage());

            assertEquals(
                    0,
                    ledgerEntryRepository.countByAccountId(sourceAccount.getId())
            );

            assertEquals(
                    0,
                    ledgerEntryRepository.countByAccountId(destinationAccount.getId())
            );
        } finally {
            accountRepository.deleteById(sourceAccount.getId());
            accountRepository.deleteById(destinationAccount.getId());
        }
    }
}
