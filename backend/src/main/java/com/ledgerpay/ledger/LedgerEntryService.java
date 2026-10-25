package com.ledgerpay.ledger;

import com.ledgerpay.account.Account;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class LedgerEntryService {

    private final LedgerEntryRepository ledgerEntryRepository;

    public LedgerEntryService(LedgerEntryRepository ledgerEntryRepository) {
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    public LedgerEntry createEntry(
            LedgerTransaction transaction,
            Account account,
            LedgerEntryType entryType,
            BigDecimal amount
    ) {
        LedgerEntry entry = new LedgerEntry(transaction, account, entryType, amount);
        return ledgerEntryRepository.save(entry);
    }
}
