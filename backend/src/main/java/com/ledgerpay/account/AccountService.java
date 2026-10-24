package com.ledgerpay.account;

import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class AccountService {

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public Account createAccount(String accountName, String currency) {
        Account account = new Account(accountName, currency, AccountStatus.ACTIVE);
        return accountRepository.save(account);
    }

    public Optional<Account> getAccountById(UUID id) {
        return accountRepository.findById(id);
    }
}
