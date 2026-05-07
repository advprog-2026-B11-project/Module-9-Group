package com.example.bidmart.bidding.exception;

public class BidConflictException extends RuntimeException {
    public BidConflictException(String message) {
        super(message);
    }
}
