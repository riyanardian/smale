package com.example.smale

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.os.Bundle
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class InputMakananManual : AppCompatActivity() {

    // =====================================================
    // UI
    // =====================================================
    private lateinit var btnTanggal: Button
    private lateinit var btnWaktu: Button

    private lateinit var rgKarbohidrat: RadioGroup
    private lateinit var rgProtein: RadioGroup
    private lateinit var rgSayur: RadioGroup
    private lateinit var rgSusu: RadioGroup

    private lateinit var btnSimpan: Button
    private lateinit var btnBatal: Button

    // =====================================================
    // PREF
    // =====================================================
    private val PREF_NAME = "makanan_pref"
    private val KEY_MAKANAN = "list_makanan"

    // =====================================================
    // DATE TIME
    // =====================================================
    private var tanggalDipilih = ""
    private var waktuDipilih = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_input_makanan_manual)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        bindView()
        setDefaultDateTime()

        btnTanggal.setOnClickListener { pilihTanggal() }
        btnWaktu.setOnClickListener { pilihWaktu() }
        btnBatal.setOnClickListener { finish() }

        btnSimpan.setOnClickListener {
            simpanDataMakanan()
        }
    }

    private fun bindView() {
        btnTanggal = findViewById(R.id.btnTanggal)
        btnWaktu = findViewById(R.id.btnWaktu)
        rgKarbohidrat = findViewById(R.id.rgKarbohidrat)
        rgProtein = findViewById(R.id.rgProtein)
        rgSayur = findViewById(R.id.rgSayur)
        rgSusu = findViewById(R.id.rgSusu)
        btnSimpan = findViewById(R.id.btnSimpan)
        btnBatal = findViewById(R.id.btnBatal)
    }

    private fun setDefaultDateTime() {
        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        tanggalDipilih = dateFormat.format(calendar.time)
        waktuDipilih = timeFormat.format(calendar.time)

        btnTanggal.text = tanggalDipilih
        btnWaktu.text = waktuDipilih
    }

    private fun pilihTanggal() {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, y, m, d ->
                tanggalDipilih = String.format("%02d/%02d/%04d", d, m + 1, y)
                btnTanggal.text = tanggalDipilih
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun pilihWaktu() {
        val calendar = Calendar.getInstance()
        TimePickerDialog(
            this,
            { _, h, m ->
                waktuDipilih = String.format("%02d:%02d", h, m)
                btnWaktu.text = waktuDipilih
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        ).show()
    }

    private fun getSelectedText(rg: RadioGroup): String {
        val id = rg.checkedRadioButtonId
        if (id == -1) return "-"

        // Cukup cari berdasarkan ID yang terpilih di dalam grup itu sendiri
        val rb = rg.findViewById<RadioButton>(id)
        return rb?.text?.toString() ?: "-"
    }

    // =====================================================
    // SIMPAN DATA (MURNI LOKAL)
    // =====================================================
    private fun simpanDataMakanan() {
        val karbo = getSelectedText(rgKarbohidrat)
        val protein = getSelectedText(rgProtein)
        val sayur = getSelectedText(rgSayur)
        val susu = getSelectedText(rgSusu)

        // Validasi: Cegah simpan jika isinya kosong semua
        if (karbo == "-" && protein == "-" && sayur == "-" && susu == "-") {
            Toast.makeText(this, "Pilih minimal satu jenis menu makanan, Yan!", Toast.LENGTH_SHORT).show()
            return
        }

        // Ambil info profil anak yang sedang dipilih
        val childPref = getSharedPreferences("child_pref", Context.MODE_PRIVATE)
        val childString = childPref.getString("selected_child", null)

        var childId = "-"
        var childNama = "Unknown"

        if (childString != null) {
            val childJson = JSONObject(childString)
            childId = childJson.optString("id", "-")
            childNama = childJson.optString("nama", "Unknown")
        }

        // Susun struktur objek JSON log makanan
        val json = JSONObject().apply {
            put("id", UUID.randomUUID().toString())
            put("child_id", childId)
            put("nama_anak", childNama)
            put("tanggal", tanggalDipilih)
            put("waktu", waktuDipilih)
            put("karbohidrat", karbo)
            put("protein", protein)
            put("sayur_buah", sayur)
            put("susu_asi", susu)
            put("created_at", System.currentTimeMillis())
        }

        // Eksekusi kunci data ke Shared Preferences HP
        simpanKePreferences(json)

        // Beri feedback instan ke user, lalu tutup halaman
        Toast.makeText(this, "Data menu makanan berhasil dicatat!", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun simpanKePreferences(json: JSONObject) {
        val pref = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val oldData = pref.getString(KEY_MAKANAN, null)
        val jsonArray = if (oldData != null) JSONArray(oldData) else JSONArray()

        jsonArray.put(json)
        pref.edit().putString(KEY_MAKANAN, jsonArray.toString()).apply()
    }
}