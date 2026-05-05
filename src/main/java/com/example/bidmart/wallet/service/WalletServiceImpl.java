package com.example.bidmart.wallet.service;

import com.example.bidmart.wallet.exception.*;
import com.example.bidmart.wallet.model.Transaction;
import com.example.bidmart.wallet.model.Wallet;
import com.example.bidmart.wallet.repository.TransactionRepository;
import com.example.bidmart.wallet.repository.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class WalletServiceImpl implements WalletService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;

    public WalletServiceImpl(WalletRepository walletRepository, TransactionRepository transactionRepository) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
    }

    @Override
    @Transactional
    public Wallet createWallet(UUID userId) {
        if (walletRepository.findByUserId(userId).isPresent()) {
            throw new WalletAlreadyExistsException("Wallet sudah ada untuk user ini.");
        }
        return walletRepository.save(new Wallet(userId));
    }

    @Override
    public Wallet getWalletByUserId(UUID userId) {
        return walletRepository.findByUserId(userId)
                .orElseThrow(() -> new WalletNotFoundException("Wallet tidak ditemukan."));
    }

    @Override
    public Wallet topUp(UUID userId, BigDecimal amount) {
        return topUp(userId, amount, UUID.randomUUID().toString());
    }

    @Override
    @Transactional
    public Wallet topUp(UUID userId, BigDecimal amount, String idempotencyKey) {
        Optional<Wallet> existing = checkIdempotency(idempotencyKey, userId);
        if (existing.isPresent()) return existing.get();

        validateAmount(amount);

        Wallet wallet = getWalletByUserId(userId);
        wallet.setBalanceAvailable(wallet.getBalanceAvailable().add(amount));
        wallet = walletRepository.save(wallet);

        Transaction tx = new Transaction(wallet.getId(), "TOPUP", amount, null);
        tx.setIdempotencyKey(idempotencyKey);
        transactionRepository.save(tx);
        
        return wallet;
    }

    @Override
    public List<Wallet> findAll() {
        return walletRepository.findAll();
    }

    @Override
    public Wallet reserveBidFunds(UUID buyerId, UUID listingId, BigDecimal reserveTarget) {
        return reserveBidFunds(buyerId, listingId, reserveTarget, UUID.randomUUID().toString());
    }

    @Override
    @Transactional
    public Wallet reserveBidFunds(UUID buyerId, UUID listingId, BigDecimal reserveTarget, String idempotencyKey) {
        Optional<Wallet> existing = checkIdempotency(idempotencyKey, buyerId);
        if (existing.isPresent()) return existing.get();

        validateAmount(reserveTarget);

        Wallet wallet = getWalletByUserId(buyerId);
        String refId = listingId.toString();

        BigDecimal currentlyHeld = calculateHeldForReference(wallet.getId(), refId);

        if (reserveTarget.compareTo(currentlyHeld) <= 0) {
            return wallet;
        }

        BigDecimal additionalLock = reserveTarget.subtract(currentlyHeld);

        if (wallet.getBalanceAvailable().compareTo(additionalLock) < 0) {
            throw new InsufficientBalanceException(
                    "Saldo tidak mencukupi. Dibutuhkan: " + additionalLock + ", tersedia: " + wallet.getBalanceAvailable());
        }

        wallet.setBalanceAvailable(wallet.getBalanceAvailable().subtract(additionalLock));
        wallet.setBalanceLocked(wallet.getBalanceLocked().add(additionalLock));
        wallet = walletRepository.save(wallet);

        Transaction tx = new Transaction(wallet.getId(), "HOLD", additionalLock, refId);
        tx.setIdempotencyKey(idempotencyKey);
        transactionRepository.save(tx);

        return wallet;
    }

    @Override
    public Wallet releaseBidFunds(UUID buyerId, UUID listingId, BigDecimal releaseAmount) {
        return releaseBidFunds(buyerId, listingId, releaseAmount, UUID.randomUUID().toString());
    }

    @Override
    @Transactional
    public Wallet releaseBidFunds(UUID buyerId, UUID listingId, BigDecimal releaseAmount, String idempotencyKey) {
        Optional<Wallet> existing = checkIdempotency(idempotencyKey, buyerId);
        if (existing.isPresent()) return existing.get();

        validateAmount(releaseAmount);

        Wallet wallet = getWalletByUserId(buyerId);
        String refId = listingId.toString();

        BigDecimal currentlyHeld = calculateHeldForReference(wallet.getId(), refId);
        if (currentlyHeld.compareTo(BigDecimal.ZERO) <= 0) {
            return wallet;
        }

        BigDecimal actualRelease = currentlyHeld.min(releaseAmount);

        wallet.setBalanceLocked(wallet.getBalanceLocked().subtract(actualRelease));
        wallet.setBalanceAvailable(wallet.getBalanceAvailable().add(actualRelease));
        wallet = walletRepository.save(wallet);

        Transaction tx = new Transaction(wallet.getId(), "REFUND", actualRelease, refId);
        tx.setIdempotencyKey(idempotencyKey);
        transactionRepository.save(tx);

        return wallet;
    }

    @Override
    public Wallet settlePayment(UUID userId, BigDecimal amount, String referenceId) {
        return settlePayment(userId, amount, referenceId, UUID.randomUUID().toString());
    }

    @Override
    @Transactional
    public Wallet settlePayment(UUID userId, BigDecimal amount, String referenceId, String idempotencyKey) {
        Optional<Wallet> existing = checkIdempotency(idempotencyKey, userId);
        if (existing.isPresent()) return existing.get();

        validateAmount(amount);

        Wallet wallet = getWalletByUserId(userId);
        if (wallet.getBalanceLocked().compareTo(amount) < 0) {
            throw new InsufficientBalanceException(
                    "Saldo terkunci tidak mencukupi. Dibutuhkan: " + amount + ", terkunci: " + wallet.getBalanceLocked());
        }

        wallet.setBalanceLocked(wallet.getBalanceLocked().subtract(amount));
        wallet = walletRepository.save(wallet);

        Transaction tx = new Transaction(wallet.getId(), "PAYMENT", amount, referenceId);
        tx.setIdempotencyKey(idempotencyKey);
        transactionRepository.save(tx);

        return wallet;
    }

    @Override
    public Wallet withdraw(UUID userId, BigDecimal amount) {
        return withdraw(userId, amount, UUID.randomUUID().toString()); 
    }

    @Override
    @Transactional
    public Wallet withdraw(UUID userId, BigDecimal amount, String idempotencyKey) {
        Optional<Wallet> existing = checkIdempotency(idempotencyKey, userId);
        if (existing.isPresent()) return existing.get();

        validateAmount(amount);

        Wallet wallet = getWalletByUserId(userId);
        if (wallet.getBalanceAvailable().compareTo(amount) < 0) {
            throw new InsufficientBalanceException(
                    "Saldo tidak mencukupi. Dibutuhkan: " + amount + ", tersedia: " + wallet.getBalanceAvailable());
        }

        wallet.setBalanceAvailable(wallet.getBalanceAvailable().subtract(amount));
        wallet = walletRepository.save(wallet);

        Transaction tx = new Transaction(wallet.getId(), "WITHDRAWAL", amount, null);
        tx.setIdempotencyKey(idempotencyKey);
        transactionRepository.save(tx);

        return wallet;
    }

    @Override
    public List<Transaction> getTransactionHistory(UUID userId) {
        Wallet wallet = getWalletByUserId(userId);
        return transactionRepository.findByWalletIdOrderByCreatedAtDesc(wallet.getId());
    }

    @Override
    public Wallet confirmDelivery(UUID sellerId, BigDecimal amount, String referenceId) {
        return confirmDelivery(sellerId, amount, referenceId, UUID.randomUUID().toString());
    }

    @Override
    @Transactional
    public Wallet confirmDelivery(UUID sellerId, BigDecimal amount, String referenceId, String idempotencyKey) {
        Optional<Wallet> existing = checkIdempotency(idempotencyKey, sellerId);
        if (existing.isPresent()) return existing.get();

        validateAmount(amount);

        Wallet sellerWallet = getWalletByUserId(sellerId);
        sellerWallet.setBalanceAvailable(sellerWallet.getBalanceAvailable().add(amount));
        sellerWallet = walletRepository.save(sellerWallet);

        Transaction tx = new Transaction(sellerWallet.getId(), "INCOME", amount, referenceId);
        tx.setIdempotencyKey(idempotencyKey);
        transactionRepository.save(tx);

        return sellerWallet;
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidAmountException("Jumlah harus lebih dari 0.");
        }
    }

    private BigDecimal calculateHeldForReference(UUID walletId, String referenceId) {
        List<Transaction> txs = transactionRepository.findByWalletIdAndReferenceId(walletId, referenceId);

        BigDecimal held = BigDecimal.ZERO;
        for (Transaction tx : txs) {
            if ("HOLD".equals(tx.getType())) {
                held = held.add(tx.getAmount());
            } else if ("REFUND".equals(tx.getType()) || "PAYMENT".equals(tx.getType())) {
                held = held.subtract(tx.getAmount());
            }
        }

        return held.max(BigDecimal.ZERO);
    }

    private Optional<Wallet> checkIdempotency(String idempotencyKey, UUID userId) {
        return transactionRepository.findByIdempotencyKey(idempotencyKey)
                .map(tx -> getWalletByUserId(userId));
    }
}
