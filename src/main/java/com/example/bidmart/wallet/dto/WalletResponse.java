package com.example.bidmart.wallet.dto;

import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalletResponse {
    private UUID userId;
    private BigDecimal balanceAvailable;
    private BigDecimal balanceLocked;
}
