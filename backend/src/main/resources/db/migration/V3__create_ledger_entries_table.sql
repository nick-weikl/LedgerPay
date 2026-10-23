CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL REFERENCES ledger_transactions(id),
    account_id UUID NOT NULL REFERENCES accounts(id),
    entry_type VARCHAR(10) NOT NULL
                            CONSTRAINT check_ledger_entry_type
                            CHECK ( entry_type IN ( 'DEBIT', 'CREDIT' )),
    amount NUMERIC(19,2) NOT NULL
                            CONSTRAINT check_ledger_entry_amount
                            CHECK ( amount > 0 ),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);