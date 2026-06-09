# SMALE (Smart Scale) - IoT & Machine Learning System

## Deskripsi Proyek

**SMALE (Smart Scale)** adalah sistem timbangan pintar berbasis **Internet of Things (IoT)** yang dirancang untuk memantau pertumbuhan anak dan mendukung deteksi dini risiko **stunting**. Sistem ini mengintegrasikan perangkat keras, aplikasi Android, backend server, serta **Machine Learning** untuk analisis data pertumbuhan secara real-time.

Sistem ini dikembangkan sebagai **Tugas Akhir**:

- **Nama** : Riyan Ardian Syah  
- **Universitas** : Universitas Teknologi Yogyakarta  

---

## Fitur Utama

- 📡 Pengukuran berat dan tinggi badan otomatis
- 📊 Pengiriman data real-time menggunakan MQTT
- 📱 Aplikasi Android untuk monitoring pertumbuhan anak
- 🧠 Analisis data menggunakan Machine Learning:
  - Random Forest (klasifikasi status gizi)
  - LSTM (prediksi pertumbuhan tinggi badan)
- 🗄️ Penyimpanan data menggunakan MySQL
- ☁️ Backend server untuk pengolahan data IoT
- 📈 Visualisasi data pertumbuhan secara digital

---

## Teknologi yang Digunakan

### Hardware
- Mappi32 (ESP32)
- Sensor Load Cell + HX711
- Sensor Ultrasonik HC-SR04

### Software & Tools
- Arduino IDE
- Android Studio
- Node.js (REST API)
- Python (Backend & Machine Learning)
- MySQL Database
- MQTT Broker
- Visual Studio Code
- Google Colab (training model ML)

---

**Gambar Alur Data**
<img width="2789" height="1504" alt="Body Fat pdf (5)" src="https://github.com/user-attachments/assets/a159318d-aaeb-487c-9326-a75f522509c8" />

---

**Gambar Alat**

<img width="257" height="339" alt="image" src="https://github.com/user-attachments/assets/4a564e7c-a852-4a86-8e55-8a1566ea8252" />

---

## 📱 Instalasi Aplikasi (APK)

Aplikasi SMALE Android dapat diinstal melalui file APK yang tersedia pada repository atau melalui link QR berikut:

 🔗 Download APK
Scan QR berikut atau buka link di bawah ini:

<img width="1000" height="1000" alt="SMALE" src="https://github.com/user-attachments/assets/46b82791-c08d-41e0-983b-08c3906563bf" />

📲 Cara Install APK

1. Buka link QR di atas atau scan menggunakan kamera HP
2. Download file APK SMALE
3. Aktifkan izin instalasi dari sumber tidak dikenal:
   - Masuk ke **Settings / Pengaturan**
   - Pilih **Security / Keamanan**
   - Aktifkan **Install unknown apps / Sumber tidak dikenal**
4. Buka file APK yang sudah di-download
5. Klik **Install**
6. Tunggu proses instalasi selesai
7. Jalankan aplikasi **SMALE**

---

## ⚠️ Catatan

- Pastikan menggunakan Android minimal versi yang didukung aplikasi
- Jika terjadi error instalasi, hapus versi lama terlebih dahulu
- Gunakan APK dari link resmi untuk menghindari file corrupt


---

### 📌 Penjelasan Detail Struktur Sistem

#### 📱 Android/
Folder ini berisi source code aplikasi mobile Android yang digunakan untuk:
- Menampilkan data hasil pengukuran berat dan tinggi badan
- Monitoring pertumbuhan anak secara real-time
- Menampilkan hasil analisis dari sistem Machine Learning
- Komunikasi dengan backend server melalui API

#### ⚙️ Arduino_IDE/
Folder ini berisi program untuk mikrokontroler ESP32 (Mappi32), yang berfungsi untuk:
- Membaca data dari sensor Load Cell (berat badan)
- Membaca data dari sensor Ultrasonik (tinggi badan)
- Mengirim data sensor ke server menggunakan MQTT
- Mengelola komunikasi IoT secara real-time

#### 📦 Build_APK/
Folder ini berisi file APK hasil build dari aplikasi Android:
- File ini dapat langsung di-install di perangkat Android
- Digunakan untuk testing atau distribusi aplikasi

#### 🖥️ Server/
Folder ini merupakan inti sistem backend yang berfungsi untuk:
- Menerima data dari ESP32 melalui MQTT atau REST API
- Menyimpan data ke database MySQL
- Mengolah data menggunakan Machine Learning (Random Forest & LSTM)
- Menyediakan API untuk aplikasi Android
- Mengatur komunikasi antar sistem IoT

#### 🌐 index.html
File ini merupakan web monitoring sederhana yang berfungsi untuk:
- Menampilkan data pengukuran secara langsung
- Visualisasi data pertumbuhan anak
- Alternatif tampilan selain aplikasi Android

#### ⚙️ .gitignore
File ini digunakan untuk:
- Mengabaikan file yang tidak perlu di-upload ke GitHub
- Menjaga repository tetap bersih dan ringan
- Contoh: file build, cache, atau file sistem

## 🔄 Alur Data Sistem

Sensor → ESP32 → MQTT → Backend Server → MySQL → Machine Learning → Android & Web


## 📌 Catatan

Struktur ini dibuat untuk mendukung sistem SMALE sebagai:
- Sistem IoT monitoring pertumbuhan anak
- Integrasi Machine Learning untuk deteksi stunting
- Sistem data real-time berbasis cloud
