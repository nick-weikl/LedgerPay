package com.ledgerpay.ledger;

import jakarta.persistence.*;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.OffsetDateTime;
import java.util.UUID;

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

    protected LedgerTransaction() {
    }

    public LedgerTransaction(String idempotencyKey, LedgerTransactionStatus status) {
        this.idempotencyKey = idempotencyKey;
        this.status = status;
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

    public void markCompleted() {
        if (this.status != LedgerTransactionStatus.PENDING) {
            throw new IllegalStateException(
                    "Only pending transactions can be completed"
            );
        }

        this.status = LedgerTransactionStatus.COMPLETED;
    }
}
