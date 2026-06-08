package com.example.smale

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONArray

class Makanan : AppCompatActivity() {

    private lateinit var btnManual: Button
    private lateinit var tvRiwayat: TextView
    private lateinit var tvGrafik: TextView
    private lateinit var progressBar: ProgressBar

    private val PREF_NAME = "makanan_pref"
    private val KEY_MAKANAN = "list_makanan"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(R.layout.activity_makanan)

        // =====================================================
// PERBAIKAN WARNA NAVIGATION BAR & STATUS BAR
// =====================================================
// 1. Bagian BAWAH (Navigation Bar): Tetap Putih + Ikon Gelap (Biar kelihatan)
        window.navigationBarColor = getColor(android.R.color.white)

// 2. Bagian ATAS (Status Bar): Kita buat Transparan/Mengikuti Tema + Ikon Gelap (Biar baterai kelihatan)
        window.statusBarColor = android.graphics.Color.TRANSPARENT

// 3. Setel agar ikon di ATAS dan di BAWAH sama-sama berwarna gelap/abu-abu (Light Mode style)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                        android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                        android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            )
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                            View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                    )
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        bindView()
        loadRiwayatMakanan()
        setupNavigation()

        // ================= BUTTON MANUAL (FIXED) =================
        btnManual.setOnClickListener {
            // Murni langsung buka halaman input manual tanpa ditumpuk dialog
            val intent = Intent(this, InputMakananManual::class.java)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        // Ini sudah benar, biar pas balik dari input manual, riwayat di layar langsung ter-update otomatis
        loadRiwayatMakanan()
    }

    private fun bindView() {
        btnManual = findViewById(R.id.btnManual)
        tvRiwayat = findViewById(R.id.tvRiwayatGizi)
        progressBar = findViewById(R.id.progressBarGizi)
        // --- TAMBAHKAN BARIS INI ---
        tvGrafik = findViewById(R.id.tvGrafikMakanan)
        progressBar = findViewById(R.id.progressBarGizi)
    }

    // ================= NAVIGATION (FIXED) =================
    private fun setupNavigation() {

        findViewById<View?>(R.id.navBeranda)?.setOnClickListener {
            startActivity(Intent(this, Beranda::class.java))
            finish() // Ditambah finish biar stack activity-nya rapi dan tidak numpuk di memori
        }

        findViewById<View?>(R.id.navRiwayat)?.setOnClickListener {
            startActivity(Intent(this, Riwayat::class.java))
            finish()
        }

        findViewById<View?>(R.id.navProfil)?.setOnClickListener {
            startActivity(Intent(this, Profil::class.java))
            finish()
        }
    }

    private fun loadRiwayatMakanan() {
        val pref = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val data = pref.getString(KEY_MAKANAN, null)

        if (data.isNullOrEmpty()) {
            tvRiwayat.text = "Belum ada data makanan"
            progressBar.progress = 0
            tvGrafik.text = "Belum ada data"
            return
        }

        val jsonArray = JSONArray(data)
        val totalData = jsonArray.length()

        val builder = StringBuilder()
        var totalBaik = 0

        // Tampilkan data terbaru di atas
        for (i in totalData - 1 downTo 0) {
            val item = jsonArray.getJSONObject(i)

            val karbo = item.optString("karbohidrat", "-")
            val protein = item.optString("protein", "-")
            val sayur = item.optString("sayur_buah", "-")
            val susu = item.optString("susu_asi", "-")
            val tanggal = item.optString("tanggal", "-")
            val waktu = item.optString("waktu", "-")

            builder.append("📅 $tanggal - ⏰ $waktu\n")
            builder.append("🍚 Karbo: $karbo | 🍗 Protein: $protein\n")
            builder.append("🥦 Sayur: $sayur | 🥛 Susu: $susu\n")
            builder.append("----------------------\n")

            // Logika: Makanan dianggap "Baik" jika Karbo & Protein tidak "Tidak"
            if (karbo != "Tidak" && protein != "Tidak") {
                totalBaik++
            }
        }

        // --- HITUNG PERSENTASE DI LUAR LOOP ---
        val persentase = (totalBaik * 100) / totalData

        // Update UI
        progressBar.progress = persentase

        val statusGizi = when {
            persentase >= 80 -> "Sangat Baik"
            persentase >= 50 -> "Cukup Baik"
            else -> "Perlu Ditingkatkan"
        }

        tvRiwayat.text = builder.toString()
        tvGrafik.text = "Status: $statusGizi ($persentase% terpenuhi)"
    }
}