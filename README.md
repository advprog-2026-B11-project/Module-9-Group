# BidMart — Group B11

## 1. Current Architecture — Context, Container, and Deployment Diagram

### System Context Diagram

System Context Diagram ini memberikan gambaran ekosistem platform BidMart. Diagram ini menempatkan BidMart sebagai sistem utama di tengah dan memetakan bagaimana sistem tersebut berinteraksi dengan para penggunanya secara langsung, serta ketergantungannya pada sistem eksternal untuk dapat menjalankan proses bisnis lelang dan marketplace secara real-time.

![Context Diagram Bidmart](<img/ContextDiagram.png>)

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


