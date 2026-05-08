package com.example.bidmart.wallet.service;

import com.example.bidmart.wallet.dto.TopUpRequest;
import com.example.bidmart.wallet.dto.WithdrawRequest;
import com.example.bidmart.wallet.exception.*;
import com.example.bidmart.wallet.model.PaymentMethod;
import com.example.bidmart.wallet.model.Transaction;
import com.example.bidmart.wallet.model.TransactionType;
import com.example.bidmart.wallet.model.Wallet;
import com.example.bidmart.wallet.repository.TransactionRepository;
import com.example.bidmart.wallet.repository.WalletRepository;
import com.example.bidmart.wallet.strategy.PaymentStrategy;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class WalletServiceImpl implements WalletService {

    private final List<PaymentStrategy> paymentStrategies;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;

    private static final BigDecimal MAX_TOPUP_AMOUNT = new BigDecimal("100000000");

    public WalletServiceImpl(List<PaymentStrategy> paymentStrategies, WalletRepository walletRepository, TransactionRepository transactionRepository) {
        this.paymentStrategies = paymentStrategies;
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
    }

    // METHOD FACTORY: Mencari strategi yang tepat berdasarkan Enum
    private PaymentStrategy getPaymentStrategy(PaymentMethod method) {
        if (method == null) {
            throw new InvalidRequestException("Metode pembayaran tidak boleh kosong.");
        }

        return paymentStrategies.stream()
                .filter(strategy -> strategy.supports(method))
                .findFirst()
                .orElseThrow(() -> new InvalidRequestException(
                        "Metode pembayaran '" + method + "' saat ini belum didukung oleh sistem."
                ));
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

    // @Override
    // public Wallet topUp(UUID userId, BigDecimal amount) {
    //     return topUp(userId, amount, UUID.randomUUID().toString());
    // }

    @Override
    @Transactional
    public Wallet topUp(UUID userId, TopUpRequest request) {
        Optional<Wallet> existing = checkIdempotency(request.getIdempotencyKey(), userId);
        if (existing.isPresent()) return existing.get();

        validateAmount(request.getAmount());
        validateTopUpAmount(request.getAmount());

        PaymentStrategy strategy = getPaymentStrategy(request.getMethod());
        strategy.validateDetails(request.getPaymentDetails());

        Wallet wallet = getWalletByUserId(userId);
        wallet.setBalanceAvailable(wallet.getBalanceAvailable().add(request.getAmount()));
        wallet = walletRepository.save(wallet);

        String note = strategy.generateTransactionNote("TOPUP", request.getAmount(), request.getPaymentDetails());
        Transaction tx = new Transaction(wallet.getId(), TransactionType.TOPUP, request.getAmount(), note);
        tx.setIdempotencyKey(request.getIdempotencyKey());
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

        Transaction tx = new Transaction(wallet.getId(), TransactionType.HOLD, additionalLock, refId);
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

        Transaction tx = new Transaction(wallet.getId(), TransactionType.REFUND, actualRelease, refId);
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

        BigDecimal heldForReference = calculateHeldForReference(wallet.getId(), referenceId);
        if (amount.compareTo(heldForReference) > 0) {
            throw new InvalidAmountException(
                    "Jumlah settlement melebihi saldo yang ditahan. Ditahan: " + heldForReference + ", diminta: " + amount);
        }

        wallet.setBalanceLocked(wallet.getBalanceLocked().subtract(amount));
        wallet = walletRepository.save(wallet);

        Transaction tx = new Transaction(wallet.getId(), TransactionType.PAYMENT, amount, referenceId);
        tx.setIdempotencyKey(idempotencyKey);
        transactionRepository.save(tx);

        return wallet;
    }

    // @Override
    // public Wallet withdraw(UUID userId, BigDecimal amount) {
    //     return withdraw(userId, amount, UUID.randomUUID().toString()); 
    // }

    @Override
    @Transactional
    public Wallet withdraw(UUID userId, WithdrawRequest request) {
        Optional<Wallet> existing = checkIdempotency(request.getIdempotencyKey(), userId);
        if (existing.isPresent()) return existing.get();

        validateAmount(request.getAmount());

        PaymentStrategy strategy = getPaymentStrategy(request.getMethod());
        strategy.validateDetails(request.getPaymentDetails());

        Wallet wallet = getWalletByUserId(userId);
        if (wallet.getBalanceAvailable().compareTo(request.getAmount()) < 0) {
            throw new InsufficientBalanceException(
                    "Saldo tidak mencukupi. Dibutuhkan: " + request.getAmount() + ", tersedia: " + wallet.getBalanceAvailable());
        }

        wallet.setBalanceAvailable(wallet.getBalanceAvailable().subtract(request.getAmount()));
        wallet = walletRepository.save(wallet);

        String note = strategy.generateTransactionNote("WITHDRAW", request.getAmount(), request.getPaymentDetails());
        Transaction tx = new Transaction(wallet.getId(), TransactionType.WITHDRAWAL, request.getAmount(), note);
        tx.setIdempotencyKey(request.getIdempotencyKey());
        transactionRepository.save(tx);

        return wallet;
    }

    @Override
    public List<Transaction> getTransactionHistory(UUID userId) {
        Wallet wallet = getWalletByUserId(userId);
        return transactionRepository.findByWalletIdOrderByCreatedAtDesc(wallet.getId());
    }

    @Override
    public Transaction getTransactionById(UUID transactionId, UUID userId) {
        Wallet wallet = getWalletByUserId(userId);
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new InvalidRequestException("Transaksi tidak ditemukan."));
        
        // Validasi: transaksi harus milik user yang request
        if (!transaction.getWalletId().equals(wallet.getId())) {
            throw new UnauthorizedException("Anda tidak memiliki akses ke transaksi ini.");
        }
        
        return transaction;
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

        Transaction tx = new Transaction(sellerWallet.getId(), TransactionType.INCOME, amount, referenceId);
        tx.setIdempotencyKey(idempotencyKey);
        transactionRepository.save(tx);

        return sellerWallet;
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidAmountException("Jumlah harus lebih dari 0.");
        }
    }

    private void validateTopUpAmount(BigDecimal amount) {
        if (amount.compareTo(MAX_TOPUP_AMOUNT) > 0) {
            throw new InvalidAmountException(
                    "Jumlah top-up tidak boleh melebihi " + MAX_TOPUP_AMOUNT + ".");
        }
    }

    private BigDecimal calculateHeldForReference(UUID walletId, String referenceId) {
        List<Transaction> txs = transactionRepository.findByWalletIdAndReferenceId(walletId, referenceId);

        BigDecimal held = BigDecimal.ZERO;
        for (Transaction tx : txs) {
            if (TransactionType.HOLD.equals(tx.getType())) {
                held = held.add(tx.getAmount());
            } else if (TransactionType.REFUND.equals(tx.getType()) || "PAYMENT".equals(tx.getType())) {
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
