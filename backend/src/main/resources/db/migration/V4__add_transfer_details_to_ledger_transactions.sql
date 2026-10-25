ALTER TABLE ledger_transactions
    ADD COLUMN source_account_id UUID,
    ADD COLUMN destination_account_id UUID,
    ADD COLUMN amount NUMERIC(19, 2),
    ADD COLUMN currency CHAR(3);