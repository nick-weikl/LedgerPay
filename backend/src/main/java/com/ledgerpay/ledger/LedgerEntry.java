package com.ledgerpay.ledger;


import com.ledgerpay.account.Account;
import jakarta.persistence.*;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false)
    private LedgerTransaction transaction;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;
    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 10)
    private LedgerEntryType entryType;
    @Column(precision = 19, scale = 2,  nullable = false)
    private BigDecimal amount;
    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected LedgerEntry() {

    }

    public LedgerEntry(
            LedgerTransaction transaction,
            Account account,
            LedgerEntryType entryType,
            BigDecimal amount
    ) {
        this.transaction = transaction;
        this.account = account;
        this.entryType = entryType;
        this.amount = amount;
    }

    public UUID getId() {
        return id;
    }

    public LedgerTransaction getTransaction() {
        return transaction;
    }

    public Account getAccount() {
        return account;
    }

    public LedgerEntryType getEntryType() {
        return entryType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
