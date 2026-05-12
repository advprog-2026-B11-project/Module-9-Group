# BidMart — Group B11

## 1. Current Architecture — Context, Container, and Deployment Diagram

### System Context Diagram

Sistem BidMart adalah platform lelang online. Pengguna dapat mendaftarkan diri, melihat katalog barang, melakukan penawaran (bidding), mengelola virtual wallet, dan melihat status pesanan mereka.

[GAMBAR CONTEXT DIAGRAM]

**Aktor yang terlibat:**
- **User/Bidder** → mengakses frontend untuk browse catalog, bid, kelola wallet
- **Admin** → mengelola listing dan user
- **BidMart Frontend** → aplikasi Next.js di Vercel
- **BidMart Backend** → Spring Boot monolith di Render
- **PostgreSQL Database** → penyimpanan data utama

---

### Container Diagram

BidMart saat ini menggunakan arsitektur **monolith** dengan satu backend Spring Boot yang menangani semua modul.

[GAMBAR CONTAINER DIAGRAM]

**Container yang ada:**
- **Frontend (Next.js)** → di-host di Vercel, berkomunikasi via REST API
- **Backend Monolith (Spring Boot)** → di-host di Render, berisi:
  - Auth Module (autentikasi & manajemen user)
  - Catalog Module (katalog & listing)
  - Bidding Module (lelang & penawaran)
  - Wallet Module (dompet & saldo)
  - Order Module (pemesanan & notifikasi)
- **Database (PostgreSQL)** → diakses langsung oleh backend

---

### Deployment Diagram

![Deployment Diagram Bidmart](<img/DeploymentDiagram.png>)

Aplikasi BidMart dibangun dengan arsitektur cloud-native yang memisahkan sisi klien dan server. Front-end dikembangkan menggunakan Next.js dan di-hosting pada Global CDN agar dapat diakses dengan cepat dan aman oleh pengguna melalui peramban web (HTTPS). Sementara itu, pusat logika bisnis aplikasi dijalankan oleh backend berarsitektur modular monolith berbasis Spring Boot (Java 21). Backend ini di-deploy sebagai web service di platform Render untuk menerima dan merespons permintaan REST API dari frontend.
Untuk penyimpanan data persisten, sistem ini menggunakan basis data PostgreSQL secara serverless di Neon Cloud yang terhubung dengan backend secara aman melalui protokol JDBC dengan enkripsi SSL. Seluruh infrastruktur ini dikelola secara otomatis melalui pipeline CI/CD di GitHub.


