package com.ledgerpay.account;

import com.ledgerpay.dto.AccountBalanceResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
public class AccountBalanceController {
    private final AccountBalanceService accountBalanceService;
    public AccountBalanceController(AccountBalanceService accountBalanceService) {
        this.accountBalanceService = accountBalanceService;
    }

    @GetMapping("/{accountId}/balance")
    public AccountBalanceResponse getAccountBalance(@PathVariable UUID accountId) {
        return accountBalanceService.calculateBalance(accountId);
    }
}
