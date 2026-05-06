package com.example.bidmart.wallet.dto;

import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HoldBalanceRequest {
    private BigDecimal amount;
    private UUID listingId;
}
