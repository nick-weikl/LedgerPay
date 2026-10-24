package com.ledgerpay.account;

import jakarta.persistence.*;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import org.hibernate.annotations.JdbcTypeCode;
import java.sql.Types;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class Account {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "account_name", nullable = false, length = 100)
    private String accountName;
    @JdbcTypeCode(Types.CHAR)
    @Column(name = "currency", nullable = false, length = 3, columnDefinition = "CHAR(3)")
    private String currency;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AccountStatus status;
    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Account() {};

    public Account(String accountName, String currency, AccountStatus status) {
        this.accountName = accountName;
        this.currency = currency;
        this.status = status;
    }

    public UUID getId() {
        return id;
    }

    public String getAccountName() {
        return accountName;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public String getCurrency() {
        return currency;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
};
