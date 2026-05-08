package com.example.bidmart.wallet.strategy;

import com.example.bidmart.wallet.model.PaymentMethod;
import java.math.BigDecimal;
import java.util.Map;

public interface PaymentStrategy {
    boolean supports(PaymentMethod method);

    // Memvalidasi data spesifik (Bank: nomor rekening, Gopay: nomor HP)
    void validateDetails(Map<String, String> details);

    String generateTransactionNote(String transactionType, BigDecimal amount, Map<String, String> details);
}
