package com.ledgerpay.controller;

import com.ledgerpay.dto.TransferRequest;
import com.ledgerpay.dto.TransferResponse;
import com.ledgerpay.ledger.LedgerPostingService;
import com.ledgerpay.ledger.LedgerTransaction;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {

    private final LedgerPostingService ledgerPostingService;

    public TransferController(LedgerPostingService ledgerPostingService) {
        this.ledgerPostingService = ledgerPostingService;
    }

    @PostMapping
    public TransferResponse createTransfer(@Valid @RequestBody TransferRequest request) {
        LedgerTransaction transaction = ledgerPostingService.postTransfer(
                request.sourceAccountId(),
                request.destinationAccountId(),
                request.amount(),
                request.idempotencyKey()
        );
        return new TransferResponse(
                transaction.getId(),
                transaction.getStatus(),
                transaction.getAmount(),
                transaction.getCurrency()
        );
    }
}