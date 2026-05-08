package com.example.bidmart.wallet.service;

import com.example.bidmart.wallet.model.Transaction;
import com.example.bidmart.wallet.model.Wallet;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface WalletService {
    Wallet createWallet(UUID userId);
    Wallet getWalletByUserId(UUID userId);
    List<Wallet> findAll();
    
    Wallet topUp(UUID userId, BigDecimal amount);
    Wallet topUp(UUID userId, BigDecimal amount, String idempotencyKey);
    Wallet withdraw(UUID userId, BigDecimal amount);
    Wallet withdraw(UUID userId, BigDecimal amount, String idempotencyKey);
    
    // Bidding
    Wallet reserveBidFunds(UUID buyerId, UUID listingId, BigDecimal reserveTarget);
    Wallet reserveBidFunds(UUID buyerId, UUID listingId, BigDecimal reserveTarget, String idempotencyKey);
    Wallet releaseBidFunds(UUID buyerId, UUID listingId, BigDecimal releaseAmount);
    Wallet releaseBidFunds(UUID buyerId, UUID listingId, BigDecimal releaseAmount, String idempotencyKey);
    
    // Settlement & Audit
    Wallet settlePayment(UUID userId, BigDecimal amount, String referenceId);
    Wallet settlePayment(UUID userId, BigDecimal amount, String referenceId, String idempotencyKey);
    Wallet confirmDelivery(UUID sellerId, BigDecimal amount, String referenceId);
    Wallet confirmDelivery(UUID sellerId, BigDecimal amount, String referenceId, String idempotencyKey);
    List<Transaction> getTransactionHistory(UUID userId);
    Transaction getTransactionById(UUID transactionId, UUID userId);
}