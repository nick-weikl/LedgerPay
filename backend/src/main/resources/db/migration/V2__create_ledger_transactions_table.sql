CREATE TABLE ledger_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL
        CONSTRAINT check_ledger_transaction_status
        CHECK ( status IN ( 'PENDING', 'COMPLETED', 'FAILED' )),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);