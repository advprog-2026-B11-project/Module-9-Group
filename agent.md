PROJECT: BidMart — Platform Lelang Real-time
REPO: https://github.com/advprog-2026-B11-project/bidmart-backend
BRANCH UTAMA: staging (semua merge ke sini)
BRANCH MASING-MASING: feature/nama-modul

TECH STACK:
- Java 21, Spring Boot 4.x, Gradle
- PostgreSQL (database)
- Lombok (boilerplate reduction)
- Spring Security + JWT (autentikasi)
- Spring Events (komunikasi async antar modul)
- Spring Retry (retry mechanism)

ARSITEKTUR: Modular Monolith
- Semua modul dalam SATU Spring Boot application
- Setiap modul punya package sendiri: com.example.bidmart.[modul]
- Komunikasi SYNC: direct method call antar service (masih satu JVM)
- Komunikasi ASYNC: Spring ApplicationEventPublisher + @TransactionalEventListener

PRINSIP YANG WAJIB DIIKUTI:
- Constructor injection WAJIB (tidak boleh @Autowired field injection)
- Semua event cross-module HARUS di package com.example.bidmart.common.event
- SOLID principles: terutama SRP dan OCP
- Clean code: method kecil, nama deskriptif, tidak ada magic string

COMMON EVENTS (sudah ada, jangan dibuat ulang):
- BidPlacedEvent(listingId, buyerId, bidAmount)
- OutbidEvent(listingId, outbidUserId, newHighestBid)  
- AuctionWonEvent(listingId, winnerId, winningPrice)
- AuctionExtendedEvent(listingId, newEndTime)
- AuctionClosedNoWinnerEvent(listingId, reason)

MODUL BIDDING (sudah selesai, sebagai referensi):
- AuctionStatus enum: DRAFT, ACTIVE, EXTENDED, CLOSED, WON, UNSOLD
- AuctionType enum: ENGLISH (extensible untuk future)
- Strategy Pattern: AuctionStrategy interface + EnglishAuctionStrategy
- State management via AuctionStatus di Listing entity
- Anti-sniping: bid dalam 2 menit terakhir → extend 2 menit
- Scheduler: cek expired auction tiap 30 detik → close otomatis
- Race condition: @Version optimistic locking + @Retryable
- Semua test pakai @ExtendWith(MockitoExtension.class) + constructor injection