package com.ledgerpay.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import com.ledgerpay.account.Account;
import com.ledgerpay.account.AccountRepository;
import com.ledgerpay.account.AccountStatus;
import com.ledgerpay.ledger.LedgerEntryRepository;
import com.ledgerpay.ledger.LedgerTransactionRepository;
import com.ledgerpay.ledger.LedgerTransactionStatus;
import com.ledgerpay.ledger.LedgerEntryType;
import java.math.BigDecimal;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
class TransferControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private LedgerTransactionRepository ledgerTransactionRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Test
    void shouldReturn400WhenTransferAmountIsInvalid() throws Exception {

        String requestBody = """
            {
                "sourceAccountId": "7deb69b9-b36e-464e-87ed-156e9616be8b",
                "destinationAccountId": "65067fe2-78dc-4cd0-a5a6-52a31f381bcb",
                "amount": -100.00,
                "idempotencyKey": "invalid-amount-test"
            }
            """;

        mockMvc.perform(
                        post("/api/transfers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation Failed"));
    }

    @Test
    void shouldReturn404WhenSourceAccountDoesNotExist() throws Exception {
        String requestBody = """
            {
                "sourceAccountId": "00000000-0000-0000-0000-000000000001",
                "destinationAccountId": "65067fe2-78dc-4cd0-a5a6-52a31f381bcb",
                "amount": 100.00,
                "idempotencyKey": "missing-source-controller-test"
            }
            """;

        mockMvc.perform(
                        post("/api/transfers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Source account not found"));
    }

    @Test
    void shouldReturn404WhenDestinationAccountDoesNotExist() throws Exception {
        String requestBody = """
            {
                "sourceAccountId": "7deb69b9-b36e-464e-87ed-156e9616be8b",
                "destinationAccountId": "00000000-0000-0000-0000-000000000002",
                "amount": 100.00,
                "idempotencyKey": "missing-destination-controller-test"
            }
            """;

        mockMvc.perform(
                        post("/api/transfers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Destination account not found"));
    }

    @Test
    void shouldReturn409WhenIdempotencyKeyIsReusedWithDifferentAmount() throws Exception {

        Account sourceAccount = accountRepository.save(
                new Account("Source Account", "USD", AccountStatus.ACTIVE)
        );

        Account destinationAccount = accountRepository.save(
                new Account("Destination Account", "USD", AccountStatus.ACTIVE)
        );

        String idempotencyKey = "conflict-controller-test-" + UUID.randomUUID();

        try {
            String firstRequest = """
                {
                    "sourceAccountId": "%s",
                    "destinationAccountId": "%s",
                    "amount": 100.00,
                    "idempotencyKey": "%s"
                }
                """.formatted(
                    sourceAccount.getId(),
                    destinationAccount.getId(),
                    idempotencyKey
            );

            String secondRequest = """
                {
                    "sourceAccountId": "%s",
                    "destinationAccountId": "%s",
                    "amount": 250.00,
                    "idempotencyKey": "%s"
                }
                """.formatted(
                    sourceAccount.getId(),
                    destinationAccount.getId(),
                    idempotencyKey
            );

            // First request should succeed
            mockMvc.perform(
                            post("/api/transfers")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(firstRequest)
                    )
                    .andExpect(status().is2xxSuccessful());

            // Second request should return HTTP 409
            mockMvc.perform(
                            post("/api/transfers")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(secondRequest)
                    )
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.error").value("Conflict"));

            // Verify the original transaction was preserved
            var savedTransaction = ledgerTransactionRepository
                    .findByIdempotencyKey(idempotencyKey)
                    .orElseThrow();

            assertEquals(
                    0,
                    savedTransaction.getAmount().compareTo(
                            new java.math.BigDecimal("100.00")
                    )
            );

            assertEquals(
                    2,
                    ledgerEntryRepository
                            .findByTransactionId(savedTransaction.getId())
                            .size()
            );

        } finally {
            // Remove the transaction and its entries first
            ledgerTransactionRepository
                    .findByIdempotencyKey(idempotencyKey)
                    .ifPresent(transaction -> {
                        ledgerEntryRepository.deleteAll(
                                ledgerEntryRepository.findByTransactionId(
                                        transaction.getId()
                                )
                        );

                        ledgerTransactionRepository.delete(transaction);
                    });

            // Remove the test accounts
            accountRepository.deleteById(sourceAccount.getId());
            accountRepository.deleteById(destinationAccount.getId());
        }
    }

    @Test
    void shouldReturnSuccessfulTransferResponse() throws Exception {

        Account sourceAccount = accountRepository.save(
                new Account("Source Account", "USD", AccountStatus.ACTIVE)
        );

        Account destinationAccount = accountRepository.save(
                new Account("Destination Account", "USD", AccountStatus.ACTIVE)
        );

        String idempotencyKey = "success-controller-test-" + UUID.randomUUID();

        try {
            String requestBody = """
                {
                    "sourceAccountId": "%s",
                    "destinationAccountId": "%s",
                    "amount": 100.00,
                    "idempotencyKey": "%s"
                }
                """.formatted(
                    sourceAccount.getId(),
                    destinationAccount.getId(),
                    idempotencyKey
            );

            mockMvc.perform(
                            post("/api/transfers")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestBody)
                    )
                    .andExpect(status().is2xxSuccessful())
                    .andExpect(jsonPath("$.transactionId").isNotEmpty())
                    .andExpect(jsonPath("$.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.amount").value(100.00))
                    .andExpect(jsonPath("$.currency").value("USD"));

            // Verify the transaction exists in PostgreSQL
            var savedTransaction = ledgerTransactionRepository
                    .findByIdempotencyKey(idempotencyKey)
                    .orElseThrow();

            assertEquals(
                    LedgerTransactionStatus.COMPLETED,
                    savedTransaction.getStatus()
            );

            assertEquals(
                    0,
                    savedTransaction.getAmount().compareTo(
                            new BigDecimal("100.00")
                    )
            );

            // Verify double-entry bookkeeping
            var entries = ledgerEntryRepository
                    .findByTransactionId(savedTransaction.getId());

            assertEquals(2, entries.size());

            var debitEntry = entries.stream()
                    .filter(e -> e.getEntryType() == LedgerEntryType.DEBIT)
                    .findFirst()
                    .orElseThrow();

            var creditEntry = entries.stream()
                    .filter(e -> e.getEntryType() == LedgerEntryType.CREDIT)
                    .findFirst()
                    .orElseThrow();

            assertEquals(sourceAccount.getId(), debitEntry.getAccount().getId());
            assertEquals(destinationAccount.getId(), creditEntry.getAccount().getId());

            assertEquals(
                    0,
                    debitEntry.getAmount().compareTo(creditEntry.getAmount())
            );

        } finally {
            ledgerTransactionRepository
                    .findByIdempotencyKey(idempotencyKey)
                    .ifPresent(transaction -> {
                        ledgerEntryRepository.deleteAll(
                                ledgerEntryRepository.findByTransactionId(
                                        transaction.getId()
                                )
                        );

                        ledgerTransactionRepository.delete(transaction);
                    });

            accountRepository.deleteById(sourceAccount.getId());
            accountRepository.deleteById(destinationAccount.getId());
        }
    }
}