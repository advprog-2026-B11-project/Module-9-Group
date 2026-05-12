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

# Dokumentasi Arsitektur Modul Order & Notification

Repositori ini memuat dokumentasi arsitektur perangkat lunak untuk Modul **Order** dan **Notification** pada sistem BidMart. Visualisasi diagram di bawah ini mengacu pada standar C4 Model, yang difokuskan pada *Component Diagram* (Level 3) dan *Code Diagram* (Level 4), serta dilengkapi dengan diagram interaksi *behavioral*.

## 1. Component Diagram
Diagram komponen ini menunjukkan struktur internal dari modul Order dan Notifikasi, serta bagaimana kedua modul ini berinteraksi satu sama lain secara *asynchronous* melalui *Event Bus* (Spring ApplicationEventPublisher).

![Component Diagram](Component%20Diagram%20(Order%20&%20Notification%20Modules).png)

## 2. Code Diagrams (UML Class Diagrams)
*Code Diagram* ini merepresentasikan secara detail elemen statis dari kode sumber (kelas, antarmuka, atribut, dan metode) yang menyusun masing-masing modul sesuai komit terakhir.

### Modul Order
Struktur kelas yang menangani entitas pesanan dan integrasi *event* dari sistem lelang.

![UML Class Diagram - Order Module](UML%20Class%20Diagram_%20Order%20Module.png)

### Modul Notification
Struktur kelas yang bertanggung jawab atas penangkapan *event* (*listeners*) dan pengiriman pesan notifikasi ke *database*.

![UML Class Diagram - Notification Module](UML%20Class%20Diagram_%20Notification%20Module.png)

## 3. Sequence Diagrams
Diagram ini melengkapi arsitektur struktural dengan menjabarkan alur perilaku (*behavioral*) dari interaksi sistem berdasarkan skenario *Event-Driven* yang spesifik.

### Skenario Konfirmasi Pesanan
Alur proses saat pembeli mengonfirmasi penerimaan barang, yang memicu perubahan status pesanan dan pengiriman *event* notifikasi ke penjual.

![Sequence Diagram - Konfirmasi Pesanan](Sequence%20Diagram_%20Skenario%20Konfirmasi%20Pesanan.png)

### Skenario Menangkap Event Lelang Selesai
Alur eksekusi asinkron di mana sistem secara otomatis membuat entitas pesanan dan menyebarkan notifikasi ke pihak terkait tepat setelah lelang ditutup.

![Sequence Diagram - Menangkap Event Lelang](Sequence%20Diagram%20(Skenario_%20Menangkap%20Event%20Lelang%20Selesai).png)