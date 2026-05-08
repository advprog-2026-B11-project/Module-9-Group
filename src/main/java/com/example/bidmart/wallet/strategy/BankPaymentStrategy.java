package com.example.bidmart.wallet.strategy;

import com.example.bidmart.wallet.exception.InvalidRequestException;
import com.example.bidmart.wallet.model.PaymentMethod;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class BankPaymentStrategy implements PaymentStrategy {

    @Override
    public boolean supports(PaymentMethod method) {
        return PaymentMethod.BANK == method;
    }

    @Override
    public void validateDetails(Map<String, String> details) {
        if (details == null || !details.containsKey("bankName") || !details.containsKey("accountNumber")) {
            throw new InvalidRequestException("Untuk metode BANK, 'bankName' dan 'accountNumber' wajib diisi.");
        }
    }

    @Override
    public String generateTransactionNote(String transactionType, BigDecimal amount, Map<String, String> details) {
        String bankName = details.get("bankName");
        String account = details.get("accountNumber");
        
        if ("TOPUP".equalsIgnoreCase(transactionType)) {
            return "Top-Up via " + bankName + " (Virtual Account: " + account + ")";
        } else {
            return "Withdraw to " + bankName + " (Rekening: " + account + ")";
        }
    }
}