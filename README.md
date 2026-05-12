# BidMart — Group B11


## 1. Current Architecture — Context, Container, and Deployment Diagram


### System Context Diagram


System Context Diagram ini memberikan gambaran ekosistem platform BidMart. Diagram ini menempatkan BidMart sebagai sistem utama di tengah dan memetakan bagaimana sistem tersebut berinteraksi dengan para penggunanya secara langsung, serta ketergantungannya pada sistem eksternal untuk dapat menjalankan proses bisnis lelang dan marketplace secara real-time.

![COntext Diagram Bidmart](<img/ContextDiagram.png>)


**Aktor yang terlibat:**
- **Pembeli/Buyer** → Pengguna yang menelusuri katalog barang, mengajukan penawaran (bid) pada lelang, menerima pembaruan harga secara real-time, serta mengelola saldo dompet digital mereka untuk keperluan transaksi. 
- **Penjual/Seller** → Pengguna yang membuat dan mendaftarkan listing barang lelang, memantau status penawaran yang masuk, dan memproses pengiriman pesanan kepada pemenang setelah lelang selesai. 
- **Administrator** → Pihak internal yang bertugas mengawasi operasional platform secara keseluruhan, memoderasi pengguna atau listing yang melanggar aturan, serta menangani sengketa transaksi antara pembeli dan penjual. 
- **Sistem Pembayaran/Payment Gateway** → Sistem eksternal pihak ketiga yang digunakan oleh BidMart untuk memproses aktivitas keuangan pengguna, seperti top-up saldo, penarikan dana (withdrawal), serta menangani permintaan penahanan dan pemindahan dana (callback status pembayaran). 
- **Sistem Notifikasi/Notification Service** → Layanan pihak ketiga yang menerima payload dari BidMart untuk mengirimkan pesan peringatan, update status lelang, atau pemberitahuan pesanan langsung kepada pengguna melalui medium seperti email atau push notification. 


---


### Container Diagram

BidMart saat ini menggunakan arsitektur **monolith** dengan satu backend Spring Boot yang menangani semua modul.

![Container Diagram Bidmart](<img/ContainerDiagram.png>)


**Container yang ada:**
- **Frontend (Next.js)** → di-host di Vercel, berkomunikasi via REST API
- **Backend Monolith (Spring Boot)** → di-host di Render, berisi:
  - Auth Module (autentikasi & manajemen user)
  - Catalog Module (katalog & listing)
  - Bidding Module (lelang & penawaran)
  - Wallet Module (dompet & saldo)
  - Order Module (pemesanan & notifikasi)
- **Database (PostgreSQL)** → diakses langsung oleh backend


## 1. API Layer (Controllers)
Semua interaksi klien masuk melalui lapisan ini. Layer ini bertugas sebagai pintu gerbang utama yang memproses permintaan HTTP dan mengembalikan respons yang sesuai.


* **Controllers:** Mencakup kelas seperti `BidController`, `WalletController`, `UserController`, `OrderController`, dan `ListingController`.
* **Security:** Lapisan ini dilindungi oleh `SecurityConfig` dan `JwtAuthenticationFilter` yang berada di package `config`. Hal ini memastikan setiap permintaan telah terautentikasi dan terotorisasi menggunakan **JWT Token**.


## 2. Core Domain Modules (Komponen Kontainer)
Aplikasi dibagi menjadi beberapa domain utama yang saling bekerja sama secara kohesif untuk menjalankan logika bisnis:


| Modul | Deskripsi | Komponen Utama |
| :--- | :--- | :--- |
| **User Module** | Mengelola profil dan keamanan pengguna. | `JwtServiceImpl`, `MfaServiceImpl`, `SessionService`. |
| **Wallet Module** | Menangani transaksi finansial dan saldo. | Logika *HoldBalance* (Penahanan Saldo). |
| **Listing Module** | Mengelola entitas barang, kategori, dan status lelang. | `ListingService`, `CategoryRepository`. |
| **Bidding Module** | Otak utama aplikasi yang mengatur jalannya lelang. | `AuctionStrategy`, `StandardBidValidatorImpl`, `AuctionExpiryScheduler`. |


## 3. Event-Driven Mechanism (Message Broker / Event Bus)
Ini adalah keunggulan arsitektur proyek ini untuk menjaga performa sistem tetap responsif (*non-blocking*). Dibandingkan melakukan pemanggilan antar-modul secara langsung (sinkron), sistem ini menggunakan pola **Publisher-Subscriber**.


### Alur Kerja Event:
1.  **Trigger:** Terjadi aksi di modul utama (misal: lelang berakhir atau tawaran masuk).
2.  **Publish:** Sistem menembakkan event terkait:
    * `AuctionWonEvent`: Ditembakkan saat pemenang lelang ditentukan.
    * `BidPlacedEvent`: Ditembakkan saat ada penawaran baru yang valid.
3.  **Subscribe (Listener):** Kelas listener bekerja di latar belakang untuk merespons event:
    * `OrderEventListener`: Membuat pesanan otomatis saat lelang dimenangkan.
    * `NotificationEventListener`: Mengirimkan notifikasi langsung ke pengguna.




## 4. Data Layer (Repositories & Database)
Setiap modul memiliki lapisan persistensi datanya sendiri untuk menjaga isolasi data dan kemudahan pemeliharaan.


* **Repositories:** Menggunakan Spring Data JPA (misal: `WalletRepository`, `OrderRepository`, `BidRepository`).
* **Database:** Menghubungkan entitas model (Java Objects) langsung ke sistem basis data relasional secara efisien melalui pemetaan ORM.


---


### Deployment Diagram

![Deployment Diagram Bidmart](<img/DeploymentDiagram.png>)

Aplikasi BidMart dibangun dengan arsitektur cloud-native yang memisahkan sisi klien dan server. Front-end dikembangkan menggunakan Next.js dan di-hosting pada Global CDN agar dapat diakses dengan cepat dan aman oleh pengguna melalui peramban web (HTTPS). Sementara itu, pusat logika bisnis aplikasi dijalankan oleh backend berarsitektur modular monolith berbasis Spring Boot (Java 21). Backend ini di-deploy sebagai web service di platform Render untuk menerima dan merespons permintaan REST API dari frontend.
Untuk penyimpanan data persisten, sistem ini menggunakan basis data PostgreSQL secara serverless di Neon Cloud yang terhubung dengan backend secara aman melalui protokol JDBC dengan enkripsi SSL. Seluruh infrastruktur ini dikelola secara otomatis melalui pipeline CI/CD di GitHub.













---

## 4. Individual Work — Component Diagram & Code Diagrams
Berikut adalah component diagram dan code diagram dari masing-masing modul.

### 4.1 Bidding Module
Modul Bidding bertanggung jawab atas seluruh mekanisme lelang dalam sistem BidMart. Modul ini menangani penempatan bid, validasi aturan lelang, strategi tipe auction, reservasi dana wallet, anti-sniping, penutupan auction otomatis, serta penerbitan domain events ke modul lain. Modul ini merupakan komponen paling kritis dalam sistem karena berinteraksi langsung dengan modul Catalog, Wallet, dan Order melalui internal Spring events.

#### Component Diagram : Bidding Module
Diagram ini menunjukkan bagaimana setiap komponen berinteraksi satu sama lain serta terdapat hubungan dengan modul eksternal (Catalog, Wallet, Auth).

![Component Diagram Bidding](<img/ComponentDiagramBidding.png>)

#### Code Diagram 1 : BidService Class Diagram
Diagram berikut menampilkan class diagram dari BidService beserta seluruh dependensinya (C4 Level 4). BidService adalah pusat dari modul Bidding yang mengorkestrasikan seluruh alur penempatan bid, mulai dari validasi input, pengambilan data listing dengan pessimistic lock, validasi business rules, reservasi dana wallet, penyimpanan bid, anti-sniping extension, hingga penerbitan domain events.

![Code Diagram 1 Bidding](<img/CodeDiagram1_Bidding.jpeg>)

Method utama placeBid() dianotasi dengan @Retryable (maksimal 3 percobaan dengan backoff 50ms × 2) untuk menangani ObjectOptimisticLockingFailureException yang dapat terjadi saat banyak bidder bersaing secara bersamaan. Jika semua retry gagal, @Recover method akan melempar BidConflictException dengan pesan yang informatif kepada user.


#### Code Diagram 2 : Bid Entity, DTOs & Repository
Diagram berikut menampilkan class diagram dari Bid entity, DTO-DTO terkait (CreateBidRequest, BidResponse, ErrorResponse), dan BidRepository (C4 Level 4). Diagram ini menggambarkan struktur data yang menjadi fondasi dari modul Bidding, termasuk field-field database, annotations JPA, serta custom query methods pada repository.

![Code Diagram 2 Bidding](<img/CodeDiagram2_Bidding.jpeg>)

Bid entity memiliki method getReservedAmount() yang mengembalikan proxyMaxLimit jika bid adalah proxy bid, atau amount jika bukan. Nilai ini digunakan oleh WalletService untuk menentukan jumlah dana yang perlu di-reserve atau di-release saat terjadi outbid.


#### Code Diagram 3 : Auction Strategy Pattern
Diagram berikut menampilkan class diagram dari implementasi Strategy Pattern untuk mendukung berbagai tipe auction (C4 Level 4). Dengan pattern ini, BidMart dapat dengan mudah menambahkan tipe lelang baru (misalnya Dutch Auction atau Sealed Bid) tanpa mengubah kode BidService.


![Code Diagram 3 Bidding](<img/CodeDiagram3_Bidding.jpeg>)

AuctionStrategyRegistry menerima semua bean implementasi AuctionStrategy dari Spring container secara otomatis melalui constructor injection, lalu memetakannya ke Map<AuctionType, AuctionStrategy>. Saat ini terdapat satu implementasi aktif yaitu EnglishAuctionStrategy (open ascending-price auction) yang memerlukan fund holding (requiresFundHolding() = true) dan menghitung minimum bid berikutnya sebagai currentHighestBid + 1.


#### Code Diagram 4 : BidRuleValidator & Domain Events
Diagram berikut menampilkan class diagram dari BidRuleValidator (interface dan implementasinya), seluruh domain events yang diterbitkan oleh modul Bidding, BiddingEventListener, serta exception-exception yang dapat dilempar (C4 Level 4).

![Code Diagram 4 Bidding](<img/CodeDiagram4_Bidding.jpeg>)


BidRuleValidator menerapkan validasi dua fase yang terpisah secara sengaja:
- Phase 1 (validateRequest) — validasi input murni tanpa I/O: null check, amount > 0, proxyMaxLimit ≥ amount. Fase ini dilakukan sebelum query apapun ke database agar sistem dapat gagal lebih awal (fail-fast)
- Phase 2 (validateBidContext) — validasi business rules setelah data listing dan highest bid tersedia: buyer ≠ seller, auction masih open, bid > current highest bid


Domain events yang diterbitkan oleh modul Bidding menggunakan Spring ApplicationEventPublisher dengan @TransactionalEventListener(AFTER_COMMIT) agar events hanya diterbitkan setelah transaksi database berhasil di-commit, mencegah terjadinya notifikasi palsu jika transaksi rollback.


