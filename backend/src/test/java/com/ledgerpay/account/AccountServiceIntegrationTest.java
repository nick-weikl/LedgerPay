package com.ledgerpay.account;

import org.springframework.transaction.annotation.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class AccountServiceIntegrationTest {

    @Autowired
    private AccountService accountService;

    @Test
    void shouldCreateAccount() {
        Account account = accountService.createAccount("Test Account", "USD");
        assertNotNull(account.getId());
        assertEquals("Test Account", account.getAccountName());
        assertEquals(AccountStatus.ACTIVE, account.getStatus());
    }

    @Test
    void shouldGetAccountById() {
        Account account = accountService.createAccount("Test Account", "USD");
        Optional<Account> result = accountService.getAccountById(account.getId());
        assertTrue(result.isPresent());
        assertEquals(account.getId(), result.get().getId());
    }

    @Test
    void shouldReturnEmptyWhenAccountDoesNotExist() {
        Optional<Account> result = accountService.getAccountById(UUID.randomUUID());
        assertTrue(result.isEmpty());
    }
}