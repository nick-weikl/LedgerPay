package com.ledgerpay.account;

import com.ledgerpay.dto.AccountBalanceResponse;
import com.ledgerpay.exception.AccountNotFoundException;
import com.ledgerpay.ledger.LedgerEntryRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class AccountBalanceService {

    private final LedgerEntryRepository ledgerEntryRepository;

    private final AccountRepository accountRepository;

    public AccountBalanceService(LedgerEntryRepository ledgerEntryRepository,  AccountRepository accountRepository) {
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.accountRepository = accountRepository;
    }

    public AccountBalanceResponse calculateBalance(UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account does not exist"));


        BigDecimal totalCredits = ledgerEntryRepository.sumCreditsByAccountId(accountId);
        BigDecimal totalDebits = ledgerEntryRepository.sumDebitsByAccountId(accountId);

        BigDecimal balance = totalCredits.subtract(totalDebits);

        return new AccountBalanceResponse(accountId, balance, account.getCurrency());
    }
}
