package com.example.bidmart.wallet.controller;

import com.example.bidmart.wallet.dto.*;
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
    public ResponseEntity<Wallet> topUp(
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

        return ResponseEntity.ok(wallet);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/list")
    public ResponseEntity<List<Wallet>> getAllWallets() {
        return ResponseEntity.ok(walletService.findAll());
    }

    @PostMapping("/hold")
    @PreAuthorize("hasRole('INTERNAL_SERVICE')")
    public ResponseEntity<Wallet> holdBalance(Authentication authentication, @RequestBody HoldBalanceRequest request) {
        UUID userId = resolveCurrentUserId(authentication);
        Wallet wallet = walletService.reserveBidFunds(userId, request.getListingId(), request.getAmount(), request.getIdempotencyKey());
        return ResponseEntity.ok(wallet);
    }

    @PostMapping("/release")
    @PreAuthorize("hasRole('INTERNAL_SERVICE')")
    public ResponseEntity<Wallet> releaseHold(Authentication authentication, @RequestBody HoldBalanceRequest request) {
        UUID userId = resolveCurrentUserId(authentication);
        Wallet wallet = walletService.releaseBidFunds(userId, request.getListingId(), request.getAmount(), request.getIdempotencyKey());
        return ResponseEntity.ok(wallet);
    }

    @PostMapping("/settle")
    @PreAuthorize("hasRole('INTERNAL_SERVICE')")
    public ResponseEntity<Wallet> settlePayment(Authentication authentication, @RequestBody HoldBalanceRequest request) {
        UUID userId = resolveCurrentUserId(authentication);
        Wallet wallet = walletService.settlePayment(userId, request.getAmount(), request.getListingId().toString(), request.getIdempotencyKey());
        return ResponseEntity.ok(wallet);
    }

    @PostMapping("/withdraw")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Wallet> withdraw(Authentication authentication, @RequestBody WithdrawRequest request) {
        UUID userId = resolveCurrentUserId(authentication);

        Wallet wallet = walletService.withdraw(userId, request.getAmount(), request.getIdempotencyKey());
        return ResponseEntity.ok(wallet);
    }

    @GetMapping("/transactions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Transaction>> getTransactionHistory(Authentication authentication) {
        UUID userId = resolveCurrentUserId(authentication);
        List<Transaction> transactions = walletService.getTransactionHistory(userId);
        return ResponseEntity.ok(transactions);
    }

    @PostMapping("/confirm-delivery")
    @PreAuthorize("hasRole('INTERNAL_SERVICE')")
    public ResponseEntity<Wallet> confirmDelivery(@RequestBody ConfirmDeliveryRequest request) {
        Wallet wallet = walletService.confirmDelivery(
            request.getSellerId(), request.getAmount(), request.getListingId().toString(), request.getIdempotencyKey());
        return ResponseEntity.ok(wallet);
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
}
