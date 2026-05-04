package com.example.bidmart.wallet.service;

import com.example.bidmart.wallet.exception.*;
import com.example.bidmart.wallet.model.Transaction;
import com.example.bidmart.wallet.model.Wallet;
import com.example.bidmart.wallet.repository.TransactionRepository;
import com.example.bidmart.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private WalletServiceImpl walletService;

    private UUID userId;
    private UUID listingId;
    private Wallet wallet;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        listingId = UUID.randomUUID();
        wallet = new Wallet(userId);
    }

    @Test
    void createWallet_success() {
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(walletRepository.save(any(Wallet.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Wallet result = walletService.createWallet(userId);

        assertNotNull(result);
        assertEquals(userId, result.getUserId());
        assertEquals(BigDecimal.ZERO, result.getBalanceAvailable());
        assertEquals(BigDecimal.ZERO, result.getBalanceLocked());
        verify(walletRepository).save(any(Wallet.class));
    }

    @Test
    void createWallet_alreadyExists_throwsException() {
        when(walletRepository.findByUserId(userId))
                .thenReturn(Optional.of(new Wallet(userId)));

        assertThrows(WalletAlreadyExistsException.class, () -> walletService.createWallet(userId));
        verify(walletRepository, never()).save(any(Wallet.class));
    }

    @Test
    void getWalletByUserId_found() {
        Wallet wallet = new Wallet(userId);
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(wallet));

        Wallet result = walletService.getWalletByUserId(userId);

        assertNotNull(result);
        assertEquals(userId, result.getUserId());
    }

    @Test
    void getWalletByUserId_notFound_throwsException() {
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThrows(WalletNotFoundException.class, () -> walletService.getWalletByUserId(userId));
    }

    @Test
    void topUp_success() {
        Wallet wallet = new Wallet(userId);
        BigDecimal amount = new BigDecimal("50000");

        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any(Wallet.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Wallet result = walletService.topUp(userId, amount);

        assertNotNull(result);
        assertEquals(new BigDecimal("50000"), result.getBalanceAvailable());
        verify(walletRepository).save(any(Wallet.class));
    }

    @Test
    void topUp_zeroAmount_throwsException() {
        assertThrows(InvalidAmountException.class, () -> walletService.topUp(userId, BigDecimal.ZERO));
        verify(walletRepository, never()).save(any(Wallet.class));
    }

    @Test
    void topUp_negativeAmount_throwsException() {
        assertThrows(InvalidAmountException.class, () -> walletService.topUp(userId, new BigDecimal("-100")));
        verify(walletRepository, never()).save(any(Wallet.class));
    }

    @Test
    void topUp_nullAmount_throwsException() {
        assertThrows(InvalidAmountException.class, () -> walletService.topUp(userId, null));
    }

    @Test
    void topUp_walletNotFound_throwsException() {
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThrows(WalletNotFoundException.class, () -> walletService.topUp(userId, new BigDecimal("10000")));
        verify(walletRepository, never()).save(any(Wallet.class));
    }

    @Test
    void reserveBidFunds_success_newHold() {
        BigDecimal reserveAmount = new BigDecimal("50000");
        wallet.setBalanceAvailable(new BigDecimal("100000"));

        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(wallet));
        when(transactionRepository.findByWalletIdAndReferenceId(any(), anyString())).thenReturn(Collections.emptyList());
        when(walletRepository.save(any(Wallet.class))).thenAnswer(i -> i.getArgument(0));

        Wallet result = walletService.reserveBidFunds(userId, listingId, reserveAmount);

        assertEquals(new BigDecimal("50000"), result.getBalanceAvailable());
        assertEquals(new BigDecimal("50000"), result.getBalanceLocked());
        verify(transactionRepository).save(argThat(tx -> tx.getType().equals("HOLD")));
    }

    @Test
    void reserveBidFunds_insufficientBalance_throwsException() {
        BigDecimal reserveAmount = new BigDecimal("200000");
        wallet.setBalanceAvailable(new BigDecimal("100000"));

        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(wallet));
        when(transactionRepository.findByWalletIdAndReferenceId(any(), anyString())).thenReturn(Collections.emptyList());

        assertThrows(InsufficientBalanceException.class, () -> 
            walletService.reserveBidFunds(userId, listingId, reserveAmount));
    }

    @Test
    void releaseBidFunds_success() {
        BigDecimal releaseAmount = new BigDecimal("30000");
        wallet.setBalanceLocked(new BigDecimal("50000"));
        
        // Mocking history transaksi agar calculateHeldForReference mengembalikan 50000
        Transaction holdTx = new Transaction(wallet.getId(), "HOLD", new BigDecimal("50000"), listingId.toString());

        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(wallet));
        when(transactionRepository.findByWalletIdAndReferenceId(any(), anyString())).thenReturn(List.of(holdTx));
        when(walletRepository.save(any(Wallet.class))).thenAnswer(i -> i.getArgument(0));

        Wallet result = walletService.releaseBidFunds(userId, listingId, releaseAmount);

        assertEquals(new BigDecimal("20000"), result.getBalanceLocked());
        assertEquals(new BigDecimal("30000"), result.getBalanceAvailable());
        verify(transactionRepository).save(argThat(tx -> tx.getType().equals("REFUND")));
    }

    @Test
    void settlePayment_success() {
        BigDecimal paymentAmount = new BigDecimal("50000");
        wallet.setBalanceLocked(new BigDecimal("50000"));

        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any(Wallet.class))).thenAnswer(i -> i.getArgument(0));

        Wallet result = walletService.settlePayment(userId, paymentAmount, "REF-123");

        assertEquals(BigDecimal.ZERO, result.getBalanceLocked());
        verify(transactionRepository).save(argThat(tx -> tx.getType().equals("PAYMENT")));
    }

    @Test
    void withdraw_insufficientBalance_throwsException() {
        BigDecimal withdrawAmount = new BigDecimal("100000");
        wallet.setBalanceAvailable(new BigDecimal("50000"));

        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(wallet));

        assertThrows(InsufficientBalanceException.class, () -> 
            walletService.withdraw(userId, withdrawAmount));
    }

    @Test
    void confirmDelivery_increasesSellerBalance() {
        BigDecimal income = new BigDecimal("75000");
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any(Wallet.class))).thenAnswer(i -> i.getArgument(0));

        Wallet result = walletService.confirmDelivery(userId, income, "REF-INV");

        assertEquals(income, result.getBalanceAvailable());
        verify(transactionRepository).save(argThat(tx -> tx.getType().equals("INCOME")));
    }

    @Test
    void validateAmount_negative_throwsException() {
        assertThrows(InvalidAmountException.class, () -> 
            walletService.topUp(userId, new BigDecimal("-500")));
    }
}
