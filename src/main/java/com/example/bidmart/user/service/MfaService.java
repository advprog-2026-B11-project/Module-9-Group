package com.example.bidmart.user.service;

public interface MfaService {
    String generateMfaSecret();
    String getQrCodeImageUri(String secret, String email);
    boolean verifyCode(String secret, String code);
}