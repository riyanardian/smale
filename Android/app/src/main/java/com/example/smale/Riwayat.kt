package com.example.smale

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class Riwayat : AppCompatActivity() {

    private val PREF_NAME = "child_pref"
    private val KEY_MEASUREMENT_HISTORY = "measurement_history"

    private lateinit var containerRiwayat: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(R.layout.activity_riwayat)

        window.navigationBarColor = getColor(android.R.color.white)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(sys.left, sys.top, sys.right, sys.bottom)
            insets
        }

        bindView()
        loadMeasurementHistory()
        setupNavigation()
    }

    // =====================================================
    // BIND
    // =====================================================
    private fun bindView() {
        containerRiwayat = findViewById(R.id.containerRiwayat)
    }

    // =====================================================
    // LOAD HISTORY
    // =====================================================
    private fun loadMeasurementHistory() {

        containerRiwayat.removeAllViews()

        val pref = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val data = pref.getString(KEY_MEASUREMENT_HISTORY, null) ?: return

        val jsonArray = JSONArray(data)

        for (i in jsonArray.length() - 1 downTo 0) {
            addMeasurementCard(jsonArray.getJSONObject(i), i)
        }
    }

    // =====================================================
    // CARD
    // =====================================================
    private fun addMeasurementCard(json: JSONObject, index: Int) {

        val card = layoutInflater.inflate(
            R.layout.item_riwayat,
            containerRiwayat,
            false
        )

        val tvTitle = card.findViewById<TextView>(R.id.tvTitle)
        val tvDetail = card.findViewById<TextView>(R.id.tvDetail)
        val btnDelete = card.findViewById<TextView>(R.id.btnHapus)

        val nama = json.optString("nama")
        val berat = json.optDouble("berat")
        val tinggi = json.optDouble("tinggi")
        val lemak = json.optDouble("lemak")
        val status = json.optString("status")

        // ================= AI (FIX STRING) =================
        val predHeight = json.optString("pred_height", "-")
        val weightPattern = json.optString("weight_pattern", "-")

        val time = json.optLong("timestamp")

        val tanggal = SimpleDateFormat(
            "dd MMM yyyy HH:mm",
            Locale.getDefault()
        ).format(Date(time))

        tvTitle.text = "📊 Data - $nama"

        tvDetail.text = """
Berat  : %.1f kg
Tinggi : %.1f cm
BMI  : %.1f %%
Status : $status

Prediksi Tinggi (AI) : $predHeight
Pola Berat           : $weightPattern

$tanggal
        """.trimIndent().format(
            berat,
            tinggi,
            lemak
        )

        // ================= DELETE =================
        btnDelete.setOnClickListener {
            deleteItem(index)
        }

        containerRiwayat.addView(card)
    }

    // =====================================================
    // DELETE ITEM
    // =====================================================
    private fun deleteItem(index: Int) {

        val pref = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val data = pref.getString(KEY_MEASUREMENT_HISTORY, null) ?: return

        val jsonArray = JSONArray(data)
        val newArray = JSONArray()

        for (i in 0 until jsonArray.length()) {
            if (i != index) {
                newArray.put(jsonArray.getJSONObject(i))
            }
        }

        pref.edit()
            .putString(KEY_MEASUREMENT_HISTORY, newArray.toString())
            .apply()

        loadMeasurementHistory()
    }

    // =====================================================
    // NAVIGATION
    // =====================================================
    private fun setupNavigation() {

        findViewById<View>(R.id.navMakanan)
            .setOnClickListener {
                startActivity(Intent(this, Makanan::class.java))
            }

        findViewById<View>(R.id.navBeranda)
            .setOnClickListener {
                startActivity(Intent(this, Beranda::class.java))
            }

        findViewById<View>(R.id.navProfil)
            .setOnClickListener {
                startActivity(Intent(this, Profil::class.java))
            }
    }
}