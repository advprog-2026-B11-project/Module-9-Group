package com.example.bidmart.wallet.controller;

import com.example.bidmart.user.model.User;
import com.example.bidmart.user.repository.UserRepository;
import com.example.bidmart.wallet.dto.*;
import com.example.bidmart.wallet.exception.*;
import com.example.bidmart.wallet.model.PaymentMethod;
import com.example.bidmart.wallet.model.Transaction;
import com.example.bidmart.wallet.model.Wallet;
import com.example.bidmart.wallet.service.WalletService;

import lombok.With;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private String idempotencyKey;
    private static final String USERNAME = "testuser";

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        listingId = UUID.randomUUID();
        idempotencyKey = UUID.randomUUID().toString();
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

        Map<String, String> paymentDetails = new HashMap<>();
        paymentDetails.put("bankName", "BCA");
        paymentDetails.put("accountNumber", "1234567890");

        TopUpRequest request = new TopUpRequest(
                new BigDecimal("50000"), 
                PaymentMethod.BANK, 
                paymentDetails, 
                idempotencyKey
        );


        when(walletService.topUp(eq(userId), any(TopUpRequest.class))).thenReturn(wallet);

        ResponseEntity<WalletResponse> response = walletController.topUp(
                userId, request, authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(new BigDecimal("150000"), response.getBody().getBalanceAvailable());
    }

    @Test
    void topUp_unauthenticated_throwsUnauthorized() {
        TopUpRequest request = new TopUpRequest(
                new BigDecimal("50000"), 
                PaymentMethod.BANK, 
                new HashMap<>(),
                idempotencyKey
        );

        assertThrows(UnauthorizedException.class,
                () -> walletController.topUp(userId, request, null));
    }

    @Test
    void topUp_nullResult_returnsBadRequest() {
        when(walletService.topUp(eq(userId), any(TopUpRequest.class))).thenReturn(null);

        TopUpRequest request = new TopUpRequest(
                new BigDecimal("-1"), 
                PaymentMethod.BANK, 
                new HashMap<>(), 
                idempotencyKey
        );

        ResponseEntity<WalletResponse> response = walletController.topUp(
                userId, request, authentication);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void topUp_forbidden_whenDifferentUser() {
        UUID otherUserId = UUID.randomUUID();

        TopUpRequest request = new TopUpRequest(
                new BigDecimal("50000"), 
                PaymentMethod.BANK, 
                new HashMap<>(), 
                idempotencyKey
        );

        assertThrows(ResponseStatusException.class,
                () -> walletController.topUp(otherUserId, request, authentication));
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
        BigDecimal amount = new BigDecimal("30000");
        wallet.setBalanceAvailable(new BigDecimal("70000"));
        wallet.setBalanceLocked(amount);
        when(walletService.reserveBidFunds(eq(userId), eq(listingId), any(), eq(idempotencyKey))).thenReturn(wallet);

        HoldBalanceRequest request = new HoldBalanceRequest();
        request.setBuyerId(userId); 
        request.setAmount(amount);
        request.setListingId(listingId);
        request.setIdempotencyKey(idempotencyKey);

        ResponseEntity<WalletResponse> response = walletController.holdBalance(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(new BigDecimal("30000"), response.getBody().getBalanceLocked());
    }

    @Test
    void holdBalance_insufficientBalance_throwsException() {
        BigDecimal amount = new BigDecimal("999999");
        when(walletService.reserveBidFunds(eq(userId), eq(listingId), eq(amount), eq(idempotencyKey)))
                .thenThrow(new InsufficientBalanceException("Saldo tidak mencukupi."));

        HoldBalanceRequest request = new HoldBalanceRequest();
        request.setBuyerId(userId); 
        request.setAmount(amount);
        request.setListingId(listingId);
        request.setIdempotencyKey(idempotencyKey);

        assertThrows(InsufficientBalanceException.class,
                () -> walletController.holdBalance(request));
    }

    // === releaseHold ===

    @Test
    void releaseHold_success() {
        BigDecimal amount = new BigDecimal("30000");
        wallet.setBalanceLocked(BigDecimal.ZERO);
        when(walletService.releaseBidFunds(eq(userId), eq(listingId), eq(amount), eq(idempotencyKey))).thenReturn(wallet);

        HoldBalanceRequest request = new HoldBalanceRequest();
        request.setBuyerId(userId);
        request.setAmount(amount);
        request.setListingId(listingId);
        request.setIdempotencyKey(idempotencyKey);

        ResponseEntity<WalletResponse> response = walletController.releaseHold(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    // === settlePayment ===

    @Test
    void settlePayment_success() {
        BigDecimal amount = new BigDecimal("30000");
        when(walletService.settlePayment(eq(userId), eq(amount), any(), eq(idempotencyKey))).thenReturn(wallet);

        HoldBalanceRequest request = new HoldBalanceRequest();
        request.setBuyerId(userId);
        request.setAmount(amount);
        request.setListingId(listingId);
        request.setIdempotencyKey(idempotencyKey);

        ResponseEntity<WalletResponse> response = walletController.settlePayment(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    // === withdraw ===

    @Test
    void withdraw_success() {
        BigDecimal amount = new BigDecimal("40000");
        wallet.setBalanceAvailable(new BigDecimal("60000"));

        Map<String, String> paymentDetails = new HashMap<>();
        paymentDetails.put("bankName", "BCA");
        paymentDetails.put("accountNumber", "1234567890");

        WithdrawRequest request = new WithdrawRequest(
                amount,
                PaymentMethod.BANK,
                paymentDetails,
                idempotencyKey
        );

        when(walletService.withdraw(eq(userId), any(WithdrawRequest.class))).thenReturn(wallet);

        ResponseEntity<WalletResponse> response = walletController.withdraw(
                authentication, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(new BigDecimal("60000"), response.getBody().getBalanceAvailable());
    }

    @Test
    void withdraw_insufficientBalance_throwsException() {
        Map<String, String> paymentDetails = new HashMap<>();
        paymentDetails.put("bankName", "BCA");
        paymentDetails.put("accountNumber", "1234567890");

        WithdrawRequest request = new WithdrawRequest(
                new BigDecimal("999999"),
                PaymentMethod.BANK,
                paymentDetails,
                idempotencyKey
        );

        when(walletService.withdraw(eq(userId), any(WithdrawRequest.class)))
                .thenThrow(new InsufficientBalanceException("Saldo tidak mencukupi."));

        assertThrows(InsufficientBalanceException.class,
                () -> walletController.withdraw(authentication, request));
    }

    // === getTransactionHistory ===

    @Test
    void getTransactionHistory_success() {
        Transaction tx = new Transaction();
        when(walletService.getTransactionHistory(userId)).thenReturn(List.of(tx));

        ResponseEntity<List<TransactionResponse>> response = walletController.getTransactionHistory(authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
    }

    // === confirmDelivery ===

    @Test
    void confirmDelivery_success() {
        UUID sellerId = UUID.randomUUID();
        Wallet sellerWallet = new Wallet(sellerId);
        sellerWallet.setBalanceAvailable(new BigDecimal("80000"));

        when(walletService.confirmDelivery(eq(sellerId), any(), any(), eq(idempotencyKey))).thenReturn(sellerWallet);

        ConfirmDeliveryRequest request = new ConfirmDeliveryRequest(sellerId, new BigDecimal("30000"), listingId, idempotencyKey);
        ResponseEntity<WalletResponse> response = walletController.confirmDelivery(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(new BigDecimal("80000"), response.getBody().getBalanceAvailable());
    }

    @Test
    void confirmDelivery_sellerNotFound_throwsException() {
        when(walletService.confirmDelivery(any(), any(), any(), eq(idempotencyKey)))
                .thenThrow(new WalletNotFoundException("Wallet seller tidak ditemukan."));

        ConfirmDeliveryRequest request = new ConfirmDeliveryRequest(
                UUID.randomUUID(), new BigDecimal("30000"), listingId, idempotencyKey);

        assertThrows(WalletNotFoundException.class, () -> walletController.confirmDelivery(request));
    }
}
