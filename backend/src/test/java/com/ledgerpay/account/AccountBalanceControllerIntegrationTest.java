package com.ledgerpay.account;

import com.ledgerpay.ledger.LedgerPostingService;
import com.ledgerpay.ledger.LedgerTransaction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.annotation.Rollback;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
public class AccountBalanceControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private LedgerPostingService ledgerPostingService;

    @Test
    public void getBalance_whenAccountHasNoEntries_returnsZero() throws Exception {
        Account account = new Account(
                "Balance Test Account",
                "USD",
                AccountStatus.ACTIVE
        );

        Account savedAccount = accountRepository.save(account);
        UUID accountId = savedAccount.getId();

        try {
            mockMvc.perform(get("/api/accounts/{accountId}/balance", accountId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.balance").value(0))
                    .andExpect(jsonPath("$.currency").value("USD"))
                    .andExpect(jsonPath("$.accountId").value(accountId.toString()));
        } finally {
            accountRepository.deleteById(accountId);
        }
    }

    @Test
    @Transactional
    @Rollback
    public void getBalance_whenAccountHasCredits_returnsPositiveBalance() throws Exception {
        Account account1 = new Account(
                "Source Test Account",
                "USD",
                AccountStatus.ACTIVE
        );
        Account account2 = new Account(
                "Balance Test Account",
                "USD",
                AccountStatus.ACTIVE
        );
        Account account3 = new Account(
                "Destination Test Account",
                "USD",
                AccountStatus.ACTIVE
        );

        Account savedAccount1 = accountRepository.save(account1);
        Account savedAccount2 = accountRepository.save(account2);
        Account savedAccount3 = accountRepository.save(account3);
        UUID accountId1 = savedAccount1.getId();
        UUID accountId2 = savedAccount2.getId();
        UUID accountId3 = savedAccount3.getId();

        ledgerPostingService.postTransfer(
                accountId1,
                accountId2,
                new BigDecimal("500.00"),
                "balance-test-incoming-" + UUID.randomUUID()
        );

        ledgerPostingService.postTransfer(
                accountId2,
                accountId3,
                new BigDecimal("150.00"),
                "balance-test-outgoing-" + UUID.randomUUID()
        );

        mockMvc.perform(get("/api/accounts/{accountId}/balance", accountId2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(350.00))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.accountId").value(accountId2.toString()));
    }

    @Test
    @Transactional
    @Rollback
    public void getBalance_whenDebitsExceedCredits_returnsNegativeBalance() throws Exception {
        Account account1 = new Account(
                "Source Test Account",
                "USD",
                AccountStatus.ACTIVE
        );
        Account account2 = new Account(
                "Balance Test Account",
                "USD",
                AccountStatus.ACTIVE
        );
        Account account3 = new Account(
                "Destination Test Account",
                "USD",
                AccountStatus.ACTIVE
        );

        Account savedAccount1 = accountRepository.save(account1);
        Account savedAccount2 = accountRepository.save(account2);
        Account savedAccount3 = accountRepository.save(account3);
        UUID accountId1 = savedAccount1.getId();
        UUID accountId2 = savedAccount2.getId();
        UUID accountId3 = savedAccount3.getId();

        ledgerPostingService.postTransfer(
                accountId1,
                accountId2,
                new BigDecimal("50.00"),
                "balance-test-incoming-" + UUID.randomUUID()
        );

        ledgerPostingService.postTransfer(
                accountId2,
                accountId3,
                new BigDecimal("200.00"),
                "balance-test-outgoing-" + UUID.randomUUID()
        );

        mockMvc.perform(get("/api/accounts/{accountId}/balance", accountId2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(-150.00))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.accountId").value(accountId2.toString()));
    }

    @Test
    public void getBalance_whenAccountDoesNotExist_returnsNotFound() throws Exception {
        UUID accountId = UUID.randomUUID();

        mockMvc.perform(get("/api/accounts/{accountId}/balance", accountId)).
                andExpect(status().isNotFound());
    }
}
