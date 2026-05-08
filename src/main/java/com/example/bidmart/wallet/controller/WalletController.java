package com.example.bidmart.wallet.controller;

import com.example.bidmart.wallet.dto.*;
import com.example.bidmart.wallet.exception.InvalidRequestException;
import com.example.bidmart.wallet.exception.UnauthorizedException;
import com.example.bidmart.wallet.model.Transaction;
import com.example.bidmart.wallet.model.Wallet;
import com.example.bidmart.wallet.service.WalletService;
import com.example.bidmart.user.model.User;
import com.example.bidmart.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/wallet")
@CrossOrigin(origins = "*")
public class WalletController {

    private final WalletService walletService;
    private final UserRepository userRepository;

    public WalletController(WalletService walletService, UserRepository userRepository) {
        this.walletService = walletService;
        this.userRepository = userRepository;
    }

    @PostMapping("/register")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Wallet> createWallet(@RequestBody CreateWalletRequest request) {
        Wallet wallet = walletService.createWallet(request.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(wallet);
    }

    @GetMapping("/balance")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Wallet> getBalance(Authentication authentication) {
        UUID userId = resolveCurrentUserId(authentication);
        Wallet wallet = walletService.getWalletByUserId(userId);
        return ResponseEntity.ok(wallet);
    }

    @GetMapping("/{userId}/balance")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Wallet> getBalance(
            @PathVariable UUID userId,
            Authentication authentication
    ) {
        ensureCurrentUser(userId, authentication);
        return getBalance(authentication);
    }

    @PostMapping("/{userId}/top-up")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<WalletResponse> topUp(
            @PathVariable UUID userId,
            @RequestBody TopUpRequest request,
            Authentication authentication
    ) {
        ensureCurrentUser(userId, authentication);
        UUID authenticatedUserId = resolveCurrentUserId(authentication);
        Wallet wallet = walletService.topUp(authenticatedUserId, request.getAmount(), request.getIdempotencyKey());

        if (wallet == null) {
            return ResponseEntity.badRequest().build();
        }

        WalletResponse response = WalletResponse.builder()
                .userId(wallet.getUserId())
                .balanceAvailable(wallet.getBalanceAvailable())
                .balanceLocked(wallet.getBalanceLocked())
                .build();

        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/list")
    public ResponseEntity<List<Wallet>> getAllWallets() {
        return ResponseEntity.ok(walletService.findAll());
    }

    @PostMapping("/hold")
    @PreAuthorize("hasRole('INTERNAL_SERVICE')")
    public ResponseEntity<WalletResponse> holdBalance(@RequestBody HoldBalanceRequest request) {
        if (request.getAmount() == null || request.getListingId() == null) {
            throw new InvalidRequestException("Amount dan listingId harus diisi.");
        }
        
        Wallet wallet = walletService.reserveBidFunds(
                request.getBuyerId(), 
                request.getListingId(), 
                request.getAmount(), 
                request.getIdempotencyKey()
        );
        
        return ResponseEntity.ok(convertToWalletResponse(wallet));
    }

    @PostMapping("/release")
    @PreAuthorize("hasRole('INTERNAL_SERVICE')")
    public ResponseEntity<WalletResponse> releaseHold(@RequestBody HoldBalanceRequest request) {
        if (request.getAmount() == null || request.getListingId() == null) {
            throw new InvalidRequestException("Amount dan listingId harus diisi.");
        }
        
        Wallet wallet = walletService.releaseBidFunds(
                request.getBuyerId(), 
                request.getListingId(), 
                request.getAmount(), 
                request.getIdempotencyKey()
        );
        
        return ResponseEntity.ok(convertToWalletResponse(wallet));
    }

    @PostMapping("/settle")
    @PreAuthorize("hasRole('INTERNAL_SERVICE')")
    public ResponseEntity<WalletResponse> settlePayment(@RequestBody HoldBalanceRequest request) {
        if (request.getAmount() == null || request.getListingId() == null) {
            throw new InvalidRequestException("Amount dan listingId harus diisi.");
        }
        
        Wallet wallet = walletService.settlePayment(
                request.getBuyerId(), 
                request.getAmount(), 
                request.getListingId().toString(), 
                request.getIdempotencyKey()
        );
        
        return ResponseEntity.ok(convertToWalletResponse(wallet));
    }

    @PostMapping("/withdraw")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<WalletResponse> withdraw(Authentication authentication, @RequestBody WithdrawRequest request) {
        UUID userId = resolveCurrentUserId(authentication);

        Wallet wallet = walletService.withdraw(
                userId, 
                request.getAmount(),  
                request.getIdempotencyKey()
        );
        
        return ResponseEntity.ok(convertToWalletResponse(wallet));
    }

    @GetMapping("/transactions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<TransactionResponse>> getTransactionHistory(Authentication authentication) {
        UUID userId = resolveCurrentUserId(authentication);
        List<Transaction> transactions = walletService.getTransactionHistory(userId);
        
        List<TransactionResponse> responses = transactions.stream()
                .map(this::convertToTransactionResponse)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/transactions/{transactionId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TransactionResponse> getTransactionDetail(
            @PathVariable UUID transactionId,
            Authentication authentication
    ) {
        UUID userId = resolveCurrentUserId(authentication);
        Transaction transaction = walletService.getTransactionById(transactionId, userId);
        
        return ResponseEntity.ok(convertToTransactionResponse(transaction));
    }

    @PostMapping("/confirm-delivery")
    @PreAuthorize("hasRole('INTERNAL_SERVICE')")
    public ResponseEntity<WalletResponse> confirmDelivery(@RequestBody ConfirmDeliveryRequest request) {
        if (request.getSellerId() == null || request.getAmount() == null) {
            throw new InvalidRequestException("SellerId dan amount harus diisi.");
        }
        
        Wallet wallet = walletService.confirmDelivery(
                request.getSellerId(), 
                request.getAmount(), 
                request.getListingId().toString(), 
                request.getIdempotencyKey()
        );
        
        return ResponseEntity.ok(convertToWalletResponse(wallet));
    }

    private UUID resolveCurrentUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("Anda harus login terlebih dahulu.");
        }

        String username = authentication.getName();
        return userRepository.findByUsername(username)
                .map(User::getId)
                .orElseThrow(() -> new UnauthorizedException("User tidak ditemukan."));
    }

    private void ensureCurrentUser(UUID userId, Authentication authentication) {
        UUID authenticatedUserId = resolveCurrentUserId(authentication);
        if (!authenticatedUserId.equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Akses ditolak.");
        }
    }

    private WalletResponse convertToWalletResponse(Wallet wallet) {
        return WalletResponse.builder()
                .userId(wallet.getUserId())
                .balanceAvailable(wallet.getBalanceAvailable())
                .balanceLocked(wallet.getBalanceLocked())
                .build();
    }

    private TransactionResponse convertToTransactionResponse(Transaction transaction) {
        return TransactionResponse.builder()
                .id(transaction.getId())
                .type(transaction.getType())
                .amount(transaction.getAmount())
                .referenceId(transaction.getReferenceId())
                .createdAt(transaction.getCreatedAt())
                .build();
    }
}
