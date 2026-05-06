package com.example.bidmart.wallet.repository;

import com.example.bidmart.wallet.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    List<Transaction> findByWalletIdOrderByCreatedAtDesc(UUID walletId);
    Optional<Transaction> findByReferenceIdAndType(String referenceId, String type);
    List<Transaction> findByWalletIdAndReferenceId(UUID walletId, String referenceId);
}
