package com.example.bidmart.wallet.dto;

import com.example.bidmart.wallet.model.TransactionType;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class TransactionResponse {
    private UUID id;
    private TransactionType type;    
    private BigDecimal amount;
    private String referenceId;
    private LocalDateTime createdAt;
}
