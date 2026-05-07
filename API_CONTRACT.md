# BidMart - Bidding Module API Contract

Versi: 1.0 | Tanggal: 2026-05-07

---

## REST Endpoints

### Bidding (`/api/bids`)

| Method | Path | Auth | Request Body | Response | Deskripsi |
|--------|------|------|-------------|---------|-----------|
| POST | `/api/bids` | Bearer JWT | `CreateBidRequest` | `BidResponse` (201) | Tempatkan bid pada sebuah listing |
| GET | `/api/bids/listing/{listingId}` | Bearer JWT | — | `List<BidResponse>` (200) | Semua bid pada listing tertentu |
| GET | `/api/bids/listing/{listingId}/highest` | Bearer JWT | — | `BidResponse` (200) | Bid tertinggi pada listing tertentu |
| GET | `/api/bids/listing/{listingId}/minimum-bid` | Bearer JWT | — | `BigDecimal` (200) | Minimum bid yang diperbolehkan berikutnya |
| GET | `/api/bids/me` | Bearer JWT | — | `List<BidResponse>` (200) | Semua bid milik user yang sedang login |
| GET | `/api/bids/buyer/{buyerId}` | Bearer JWT | — | `List<BidResponse>` (200) | Semua bid milik buyer tertentu (hanya pemilik) |

#### `CreateBidRequest`
```json
{
  "listingId": "uuid",
  "amount": 150000,
  "proxyBid": false,
  "proxyMaxLimit": null
}
```

#### `BidResponse`
```json
{
  "id": "uuid",
  "listingId": "uuid",
  "buyerId": "uuid",
  "amount": 150000,
  "proxyBid": false,
  "proxyMaxLimit": null,
  "createdAt": "2026-05-07T10:00:00"
}
```

### Listing (`/listings`)

| Method | Path | Auth | Request Body | Response | Deskripsi |
|--------|------|------|-------------|---------|-----------|
| POST | `/listings` | Bearer JWT | `Listing` | `Listing` (200) | Buat listing lelang baru |
| GET | `/listings` | — | — | `List<Listing>` (200) | Ambil semua listing |
| GET | `/listings/{id}` | — | — | `Listing` (200) | Ambil listing berdasarkan ID |
| PUT | `/listings/{id}` | Bearer JWT | `Listing` | `Listing` (200) | Update listing (hanya saat tidak aktif) |
| DELETE | `/listings/{id}` | Bearer JWT | — | 204 No Content | Hapus listing (hanya saat tidak aktif) |

---

## HTTP Status Codes

| Kode | Kondisi |
|------|---------|
| 201 | Bid berhasil ditempatkan |
| 400 | Validasi gagal (BidValidationException) |
| 403 | Akses ditolak (bukan pemilik resource) |
| 404 | Resource tidak ditemukan |
| 409 | Konflik bid (OptimisticLockingFailure setelah 3x retry) |
| 422 | Saldo tidak mencukupi |
| 500 | Internal server error |

---

## Events Published (dari Bidding Module)

| Event Class | Package | Fields | Dipublish Ketika |
|-------------|---------|--------|-----------------|
| `BidPlacedEvent` | `common.event` | `listingId`, `buyerId`, `bidAmount` | Bid berhasil ditempatkan |
| `OutbidEvent` | `common.event` | `listingId`, `outbidUserId`, `newHighestBid` | User dikalahkan oleh bidder baru |
| `AuctionWonEvent` | `common.event` | `listingId`, `winnerId`, `winningPrice` | Lelang ditutup dengan pemenang |
| `AuctionClosedNoWinnerEvent` | `common.event` | `listingId`, `reason` | Lelang ditutup tanpa pemenang |
| `AuctionExtendedEvent` | `common.event` | `listingId`, `newEndTime` | Lelang diperpanjang karena anti-sniping |

---

## Events Consumed (oleh Bidding Module)

Bidding module tidak consume events dari modul lain.

---

## Interfaces Dibutuhkan dari Modul Lain

| Dari Modul | Method | Dipakai Untuk |
|-----------|--------|---------------|
| Wallet | `WalletService.reserveBidFunds(userId, listingId, amount)` | Hold dana saat bid ditempatkan |
| Wallet | `WalletService.releaseBidFunds(userId, listingId, amount)` | Lepas hold dana saat kalah bid |
| Wallet | `WalletService.settlePayment(userId, amount, referenceId)` | Bayar harga final saat menang lelang |

---

## Auction Lifecycle

```
DRAFT → ACTIVE → EXTENDED → WON
                           → UNSOLD
                           → CLOSED
```

- **ACTIVE**: Lelang berjalan normal, menerima bid
- **EXTENDED**: Diperpanjang karena anti-sniping (bid masuk dalam 2 menit terakhir)
- **WON**: Reserve price terpenuhi, ada pemenang
- **UNSOLD**: Reserve price tidak terpenuhi atau tidak ada bid
- **CLOSED**: Ditutup secara manual

---

## Strategy Pattern

Tipe lelang didefinisikan via `AuctionType` enum dan diproses oleh `AuctionStrategy` implementasi:

| AuctionType | Strategy Class | Keterangan |
|-------------|---------------|------------|
| `ENGLISH` | `EnglishAuctionStrategy` | Bid harus di atas bid tertinggi saat ini; hold dana aktif |

Menambah tipe lelang baru: buat implementasi baru dari `AuctionStrategy`, annotate `@Component` — tidak perlu ubah kode yang ada.

---

## Catatan Implementasi

- Semua event cross-module ada di package `com.example.bidmart.common.event`
- `AuctionClosingService` dijalankan oleh scheduler setiap **30 detik** untuk menutup lelang yang sudah berakhir
- **Anti-sniping**: Bid dalam 2 menit terakhir sebelum `endTime` → `endTime` diperpanjang 2 menit dari waktu bid
- **Race condition**: `@Retryable` pada `placeBid()` — maksimal 3x retry saat `OptimisticLockingFailureException`; setelah itu HTTP 409 dikembalikan
- **Pessimistic locking**: `findByIdWithLock()` digunakan saat `placeBid()` untuk mencegah race condition pada data listing
- Semua dependency injection menggunakan **constructor injection** (tidak ada `@Autowired` field)
