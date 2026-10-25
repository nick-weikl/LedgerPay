package com.ledgerpay.dto;

import com.ledgerpay.ledger.LedgerTransactionStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferResponse(
        UUID transactionId,
        LedgerTransactionStatus status,
        BigDecimal amount,
        String currency
) {
}