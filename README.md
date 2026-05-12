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



## 2. The future architecture of the group B11
Setelah melakukan Risk Storming pada arsitektur BidMart saat ini, kami mengidentifikasi sejumlah risiko signifikan yang dapat mengancam ketersediaan, skalabilitas, dan integritas data apabila sistem mengalami peningkatan beban besar (misalnya ribuan auction berjalan bersamaan dengan puluhan ribu bidder aktif secara bersamaan).


### Risk Matrix pada Arsitektur Monolith Saat Ini
Setelah melakukan Risk Storming pada arsitektur BidMart saat ini yang berbasis Spring Boot modular monolith, kami mengidentifikasi sejumlah risiko krusial yang mengancam ketersediaan, skalabilitas, dan integritas data saat sistem menghadapi lonjakan beban (misalnya ribuan auction dan puluhan ribu bidder aktif secara bersamaan). Berdasarkan matriks risiko, ancaman terbesar bermuara pada jalur transaksi utama dan keamanan. Dari sisi transaksi, race condition akibat penawaran serentak (bidding concurrency) dapat memicu kesalahan penentuan pemenang atau penahanan saldo ganda. Selain itu, inkonsistensi pada operasi Modul Wallet—seperti hold, release, atau settlement—berisiko fatal menyebabkan saldo negatif atau double spend. Dari segi keamanan dan aliran data, ketiadaan kontrol akses terpusat dapat meloloskan aksi pengguna yang tidak sah, sementara hilangnya event penting seperti BidPlaced atau WinnerDetermined akan membuat modul Katalog, Pesanan, dan Notifikasi gagal bereaksi. 






### Future Architecture Diagram
Berdasarkan hasil risk storming di atas, kami merancang ulang arsitektur kami.


#### Future Context Diagram
![alt](<img/ContextDiagramFuture.png>)


#### Future Container Diagram
![alt](<img/ContainerDiagramFuture.png>)


Untuk memitigasi risiko-risiko di atas, future architecture BidMart dirancang ulang menjadi microservices. Perombakan ini dilakukan karena setiap modul memiliki karakteristik beban yang ketimpang: Modul Bidding menuntut pemrosesan yang sangat cepat, Modul Wallet mewajibkan jaminan integritas finansial absolut (zero error), sementara Modul Notification lebih ideal diproses secara asinkron. Sebagai solusi, tanggung jawab dipecah ke dalam layanan-layanan independen dengan database mandiri. API Gateway dan Auth Service diimplementasikan sebagai pusat validasi identitas di garda depan. Untuk menjaga integritas bidding, sistem menerapkan penguncian (lock) per listing dan mewajibkan panggilan sinkronus (sync call) ke Wallet Service guna menahan saldo secara atomik sebelum penawaran diterima. Sementara itu, alur kerja lintas layanan (cross-service workflow) ditangani menggunakan message broker yang tangguh, sehingga layanan pendukung dapat bereaksi secara asinkron tanpa memblokir performa utama sistem. 

## 3. Explanation of Risk Storming


### Risk Storming: Identification


Pada tahap identification, setiap anggota tim secara individual mengidentifikasi area yang memiliki potensi risiko pada arsitektur BidMart. Karena BidMart masih menggunakan arsitektur modular monolith dengan single Spring Boot application dan single PostgreSQL database, beberapa risiko utama yang ditemukan adalah bottleneck pada proses auction/bidding, kemungkinan race condition saat banyak user melakukan bidding secara bersamaan, serta PostgreSQL yang menjadi single point of failure. Selain itu, authentication module dan REST API juga dianggap memiliki risiko menengah karena berkaitan dengan keamanan JWT dan performa request handling.


![Risk Storming Identification](img/risk_storming_identification.png)


---


### Risk Storming: Consensus


Pada tahap consensus, seluruh anggota tim mendiskusikan hasil identification untuk menentukan tingkat risiko akhir yang paling realistis berdasarkan implementasi aktual project. Hasil diskusi menunjukkan bahwa Auction/Bidding Module dan PostgreSQL Database memiliki tingkat risiko tertinggi karena berhubungan langsung dengan konsistensi transaksi, concurrent access, dan ketergantungan seluruh sistem terhadap satu database utama. Sementara itu, module lain seperti Notification Module dianggap memiliki risiko lebih rendah karena kompleksitas dan dependency yang lebih kecil dibanding module bidding.


![Risk Storming Consensus](img/risk_storming_consensus.png)


---


### Risk Storming: Mitigation


Pada tahap mitigation, tim menentukan solusi yang realistis untuk mengurangi risiko tanpa melakukan overengineering terhadap project. Tim memutuskan untuk tidak langsung bermigrasi ke microservices atau menambahkan infrastruktur kompleks seperti Redis dan distributed queue karena belum sesuai dengan kebutuhan dan skala project saat ini. Sebagai gantinya, mitigasi difokuskan pada optimasi query dan indexing database, locking mechanism pada proses bidding, peningkatan validasi JWT, penambahan automated testing dan CI/CD health checks, serta refactoring modular separation agar maintainability dan reliability sistem meningkat secara bertahap.


![Risk Storming Mitigation](img/risk_storming_mitigation.png)




== Commit message : "3. Explanation of risk storming of the group B11"



