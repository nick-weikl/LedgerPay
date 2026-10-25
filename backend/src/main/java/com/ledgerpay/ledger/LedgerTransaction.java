package com.ledgerpay.ledger;

import jakarta.persistence.*;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "ledger_transactions")
public class LedgerTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private LedgerTransactionStatus status;
    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;
    @Column(name = "source_account_id")
    private UUID sourceAccountId;
    @Column(name = "destination_account_id")
    private UUID destinationAccountId;
    @Column(name = "amount", precision = 19, scale = 2)
    private BigDecimal amount;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", length = 3, columnDefinition = "CHAR(3)")
    private String currency;

    protected LedgerTransaction() {
    }

    public LedgerTransaction(String idempotencyKey, LedgerTransactionStatus status) {
        this.idempotencyKey = idempotencyKey;
        this.status = status;
    }

    public LedgerTransaction(
            String idempotencyKey,
            LedgerTransactionStatus status,
            UUID sourceAccountId,
            UUID destinationAccountId,
            BigDecimal amount,
            String currency
    ) {
        this.idempotencyKey = idempotencyKey;
        this.status = status;
        this.sourceAccountId = sourceAccountId;
        this.destinationAccountId = destinationAccountId;
        this.amount = amount;
        this.currency = currency;
    }

    public UUID getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public LedgerTransactionStatus getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public UUID getSourceAccountId() {
        return sourceAccountId;
    }

    public UUID getDestinationAccountId() {
        return destinationAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void markCompleted() {
        if (this.status != LedgerTransactionStatus.PENDING) {
            throw new IllegalStateException(
                    "Only pending transactions can be completed"
            );
        }

        this.status = LedgerTransactionStatus.COMPLETED;
    }
}
