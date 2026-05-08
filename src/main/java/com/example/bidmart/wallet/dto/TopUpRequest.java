package com.example.bidmart.wallet.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;
import java.util.Map;

import com.example.bidmart.wallet.model.PaymentMethod;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TopUpRequest {
    private BigDecimal amount;
    private PaymentMethod method;             
    private Map<String, String> paymentDetails;
    private String idempotencyKey;
}