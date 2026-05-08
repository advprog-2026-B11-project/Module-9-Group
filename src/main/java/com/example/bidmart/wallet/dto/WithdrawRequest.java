package com.example.bidmart.wallet.dto;

import lombok.*;
import java.math.BigDecimal;
import java.util.Map;

import com.example.bidmart.wallet.model.PaymentMethod;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawRequest {
    private BigDecimal amount;
    private PaymentMethod method; 
    private Map<String, String> paymentDetails;
    private String idempotencyKey;
}
