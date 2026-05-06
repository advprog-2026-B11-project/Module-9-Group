package com.example.bidmart.wallet.controller;

import com.example.bidmart.user.model.User;
import com.example.bidmart.user.repository.UserRepository;
import com.example.bidmart.wallet.dto.*;
import com.example.bidmart.wallet.exception.*;
import com.example.bidmart.wallet.model.Transaction;
import com.example.bidmart.wallet.model.Wallet;
import com.example.bidmart.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WalletControllerTest {

    @Mock
    private WalletService walletService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private WalletController walletController;

    private UUID userId;
    private UUID listingId;
    private Wallet wallet;
    private static final String USERNAME = "testuser";

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        listingId = UUID.randomUUID();
        wallet = new Wallet(userId);
        wallet.setBalanceAvailable(new BigDecimal("100000"));

        User user = new User();
        user.setId(userId);
        user.setUsername(USERNAME);

        when(authentication.getName()).thenReturn(USERNAME);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
    }

    // === createWallet ===

    @Test
    void createWallet_success() {
        when(walletService.createWallet(userId)).thenReturn(wallet);

        ResponseEntity<Wallet> response = walletController.createWallet(new CreateWalletRequest(userId));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void createWallet_alreadyExists_throwsException() {
        when(walletService.createWallet(userId)).thenThrow(new WalletAlreadyExistsException("Wallet sudah ada."));

        assertThrows(WalletAlreadyExistsException.class,
                () -> walletController.createWallet(new CreateWalletRequest(userId)));
    }

    // === getBalance ===

    @Test
    void getBalance_success() {
        when(walletService.getWalletByUserId(userId)).thenReturn(wallet);

        ResponseEntity<Wallet> response = walletController.getBalance(authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(new BigDecimal("100000"), response.getBody().getBalanceAvailable());
    }

    @Test
    void getBalance_byUserId_success() {
        when(walletService.getWalletByUserId(userId)).thenReturn(wallet);

        ResponseEntity<Wallet> response = walletController.getBalance(userId, authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void getBalance_forbidden_whenDifferentUser() {
        UUID otherUserId = UUID.randomUUID();

        assertThrows(ResponseStatusException.class,
                () -> walletController.getBalance(otherUserId, authentication));
    }

    // === topUp ===

    @Test
    void topUp_success() {
        wallet.setBalanceAvailable(new BigDecimal("150000"));
        when(walletService.topUp(eq(userId), any())).thenReturn(wallet);

        ResponseEntity<Wallet> response = walletController.topUp(
                userId, new TopUpRequest(new BigDecimal("50000")), authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(new BigDecimal("150000"), response.getBody().getBalanceAvailable());
    }

    @Test
    void topUp_unauthenticated_throwsUnauthorized() {
        assertThrows(UnauthorizedException.class,
                () -> walletController.topUp(userId, new TopUpRequest(new BigDecimal("50000")), null));
    }

    @Test
    void topUp_nullResult_returnsBadRequest() {
        when(walletService.topUp(eq(userId), any())).thenReturn(null);

        ResponseEntity<Wallet> response = walletController.topUp(
                userId, new TopUpRequest(new BigDecimal("-1")), authentication);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void topUp_forbidden_whenDifferentUser() {
        UUID otherUserId = UUID.randomUUID();

        assertThrows(ResponseStatusException.class,
                () -> walletController.topUp(otherUserId, new TopUpRequest(new BigDecimal("50000")), authentication));
    }

    // === getAllWallets ===

    @Test
    void getAllWallets_success() {
        when(walletService.findAll()).thenReturn(List.of(wallet));

        ResponseEntity<List<Wallet>> response = walletController.getAllWallets();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
    }

    // === holdBalance ===

    @Test
    void holdBalance_success() {
        wallet.setBalanceAvailable(new BigDecimal("70000"));
        wallet.setBalanceLocked(new BigDecimal("30000"));
        when(walletService.reserveBidFunds(eq(userId), eq(listingId), any())).thenReturn(wallet);

        HoldBalanceRequest request = new HoldBalanceRequest(new BigDecimal("30000"), listingId);
        ResponseEntity<Wallet> response = walletController.holdBalance(authentication, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(new BigDecimal("30000"), response.getBody().getBalanceLocked());
    }

    @Test
    void holdBalance_insufficientBalance_throwsException() {
        when(walletService.reserveBidFunds(eq(userId), eq(listingId), any()))
                .thenThrow(new InsufficientBalanceException("Saldo tidak mencukupi."));

        HoldBalanceRequest request = new HoldBalanceRequest(new BigDecimal("999999"), listingId);

        assertThrows(InsufficientBalanceException.class,
                () -> walletController.holdBalance(authentication, request));
    }

    // === releaseHold ===

    @Test
    void releaseHold_success() {
        wallet.setBalanceLocked(BigDecimal.ZERO);
        when(walletService.releaseBidFunds(eq(userId), eq(listingId), any())).thenReturn(wallet);

        HoldBalanceRequest request = new HoldBalanceRequest(new BigDecimal("30000"), listingId);
        ResponseEntity<Wallet> response = walletController.releaseHold(authentication, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    // === settlePayment ===

    @Test
    void settlePayment_success() {
        when(walletService.settlePayment(eq(userId), any(), any())).thenReturn(wallet);

        HoldBalanceRequest request = new HoldBalanceRequest(new BigDecimal("30000"), listingId);
        ResponseEntity<Wallet> response = walletController.settlePayment(authentication, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    // === withdraw ===

    @Test
    void withdraw_success() {
        wallet.setBalanceAvailable(new BigDecimal("60000"));
        when(walletService.withdraw(eq(userId), any())).thenReturn(wallet);

        ResponseEntity<Wallet> response = walletController.withdraw(
                authentication, new WithdrawRequest(new BigDecimal("40000")));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(new BigDecimal("60000"), response.getBody().getBalanceAvailable());
    }

    @Test
    void withdraw_insufficientBalance_throwsException() {
        when(walletService.withdraw(eq(userId), any()))
                .thenThrow(new InsufficientBalanceException("Saldo tidak mencukupi."));

        assertThrows(InsufficientBalanceException.class,
                () -> walletController.withdraw(authentication, new WithdrawRequest(new BigDecimal("999999"))));
    }

    // === getTransactionHistory ===

    @Test
    void getTransactionHistory_success() {
        Transaction tx = new Transaction();
        when(walletService.getTransactionHistory(userId)).thenReturn(List.of(tx));

        ResponseEntity<List<Transaction>> response = walletController.getTransactionHistory(authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
    }

    // === confirmDelivery ===

    @Test
    void confirmDelivery_success() {
        UUID sellerId = UUID.randomUUID();
        Wallet sellerWallet = new Wallet(sellerId);
        sellerWallet.setBalanceAvailable(new BigDecimal("80000"));

        when(walletService.confirmDelivery(eq(sellerId), any(), any())).thenReturn(sellerWallet);

        ConfirmDeliveryRequest request = new ConfirmDeliveryRequest(sellerId, new BigDecimal("30000"), listingId);
        ResponseEntity<Wallet> response = walletController.confirmDelivery(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(new BigDecimal("80000"), response.getBody().getBalanceAvailable());
    }

    @Test
    void confirmDelivery_sellerNotFound_throwsException() {
        when(walletService.confirmDelivery(any(), any(), any()))
                .thenThrow(new WalletNotFoundException("Wallet seller tidak ditemukan."));

        ConfirmDeliveryRequest request = new ConfirmDeliveryRequest(
                UUID.randomUUID(), new BigDecimal("30000"), listingId);

        assertThrows(WalletNotFoundException.class, () -> walletController.confirmDelivery(request));
    }
}
