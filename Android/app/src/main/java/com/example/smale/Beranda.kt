package com.example.smale

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

class Beranda : AppCompatActivity() {

    // ================= PREF =================
    private val PREF_NAME = "child_pref"
    private val KEY_CHILD_LIST = "child_list"
    private val KEY_SELECTED_CHILD = "selected_child"
    private val KEY_MEASUREMENT_HISTORY = "measurement_history"

    // ================= UI =================
    private lateinit var tvSapaanUser: TextView
    private lateinit var tvStatusKoneksi: TextView
    private lateinit var tvNama: TextView
    private lateinit var tvUsia: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvBerat: TextView
    private lateinit var tvTinggi: TextView
    private lateinit var tvLemak: TextView
    private lateinit var tvTambahAnak: TextView
    private lateinit var tvAiTinggi: TextView
    private lateinit var tvAiBerat: TextView
    private lateinit var imgAnak: ImageView

    private lateinit var mqttManager: MqttManager
    private lateinit var loadingDialog: Dialog

    private val TOPIC_SMALE = "smale/device/result"
    private val TOPIC_AI = "smale/server/result"

    // =====================================================
    // ON CREATE
    // =====================================================
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_beranda)

        // =====================================================
        // PERBAIKAN WARNA NAVIGATION BAR & STATUS BAR
        // =====================================================
        window.navigationBarColor = getColor(android.R.color.white)
        window.statusBarColor = android.graphics.Color.TRANSPARENT

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
        setupLoading()
        setupSapaanUser()
        loadChildData()
        setupNavigation()

        // Inisialisasi dan jalankan koneksi MQTT secara Global
        mqttManager = MqttManager()
        initGlobalMqtt()

        // ================= BUTTON MULAI =================
        findViewById<View>(R.id.btnMulai).setOnClickListener {
            startMeasurement()
        }

        // ================= CARD INFO =================
        findViewById<View>(R.id.cardInfo).setOnClickListener {
            showChildSelectionDialog()
        }

        // ================= TAMBAH ANAK =================
        tvTambahAnak.setOnClickListener {
            val intent = Intent(this, InputDataAnak::class.java)
            startActivityForResult(intent, 100)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK) {
            loadChildData()
        }
    }

    private fun bindView() {
        tvStatusKoneksi = findViewById(R.id.status_con)
        tvSapaanUser = findViewById(R.id.tvSapaan)
        tvNama = findViewById(R.id.tvNamaAnak)
        tvUsia = findViewById(R.id.tvUsia)
        tvStatus = findViewById(R.id.tvStatus)
        tvBerat = findViewById(R.id.tvBerat)
        tvTinggi = findViewById(R.id.tvTinggi)
        tvLemak = findViewById(R.id.tvLemak)
        tvTambahAnak = findViewById(R.id.btnAddChild)
        imgAnak = findViewById(R.id.imgAnak)
        tvAiTinggi = findViewById(R.id.tvPrediksiTinggi)
        tvAiBerat = findViewById(R.id.tvPolaBerat)
    }

    private fun setupSapaanUser() {
        val jam = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val sapaanWaktu = when (jam) {
            in 4..10 -> "Selamat Pagi"
            in 11..14 -> "Selamat Siang"
            in 15..18 -> "Selamat Sore"
            else -> "Selamat Malam"
        }
        tvSapaanUser.text = "$sapaanWaktu, Riyan!"
    }

    private fun setupNavigation() {
        findViewById<View>(R.id.navMakanan).setOnClickListener {
            startActivity(Intent(this, Makanan::class.java))
        }
        findViewById<View>(R.id.navRiwayat).setOnClickListener {
            startActivity(Intent(this, Riwayat::class.java))
        }
        findViewById<View>(R.id.navProfil).setOnClickListener {
            startActivity(Intent(this, Profil::class.java))
        }
    }

    // ================= PREF HELPERS =================
    private fun getChildList(): JSONArray {
        val data = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getString(KEY_CHILD_LIST, null)
        return if (data != null) JSONArray(data) else JSONArray()
    }

    private fun saveChildList(array: JSONArray) {
        getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().putString(KEY_CHILD_LIST, array.toString()).apply()
    }

    private fun saveSelectedChild(json: JSONObject) {
        getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().putString(KEY_SELECTED_CHILD, json.toString()).apply()
    }

    private fun getSelectedChild(): JSONObject? {
        val data = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getString(KEY_SELECTED_CHILD, null)
        return data?.let { JSONObject(it) }
    }

    private fun loadChildData() {
        val selected = getSelectedChild()
        if (selected != null) {
            showChild(selected)
        }
    }

    private fun showChild(json: JSONObject) {
        tvNama.text = json.getString("nama")
        tvUsia.text = "Usia: ${json.getInt("usia")} bulan"
        tvStatus.text = "Status: Belum diukur"

        val fotoBase64 = json.optString("foto", "")
        if (fotoBase64.isNotEmpty()) {
            try {
                val decodedBytes = Base64.decode(fotoBase64, Base64.DEFAULT)
                val bitmap: Bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                imgAnak.setImageBitmap(bitmap)
            } catch (e: Exception) {
                imgAnak.setImageResource(R.drawable.profil)
            }
        } else {
            imgAnak.setImageResource(R.drawable.profil)
        }
    }

    // ================= DIALOG PILIH ANAK =================
    private fun showChildSelectionDialog() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_pilih_anak)
        dialog.setCancelable(true)

        val listView = dialog.findViewById<ListView>(R.id.listAnak)
        val childArray = getChildList()
        val childList = mutableListOf<JSONObject>()

        for (i in 0 until childArray.length()) {
            childList.add(childArray.getJSONObject(i))
        }

        if (childList.isEmpty()) {
            dialog.dismiss()
            return
        }

        val adapter = object : ArrayAdapter<JSONObject>(this, R.layout.item_anak_dialog, childList) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: layoutInflater.inflate(R.layout.item_anak_dialog, parent, false)
                val tvNamaItem = view.findViewById<TextView>(R.id.tvNamaItem)
                val tvDetailItem = view.findViewById<TextView>(R.id.tvDetailItem)
                val btnHapus = view.findViewById<TextView>(R.id.btnHapus)

                val child = childList[position]
                tvNamaItem.text = child.getString("nama")
                tvDetailItem.text = "Usia: ${child.getInt("usia")} bulan"

                view.setOnClickListener {
                    saveSelectedChild(child)
                    showChild(child)
                    dialog.dismiss()
                }

                btnHapus.setOnClickListener {
                    childList.removeAt(position)
                    val newArray = JSONArray()
                    childList.forEach { newArray.put(it) }
                    saveChildList(newArray)
                    notifyDataSetChanged()
                    if (childList.isEmpty()) {
                        dialog.dismiss()
                    }
                }
                return view
            }
        }
        listView.adapter = adapter
        dialog.show()
    }

    // =====================================================
    // INITIALIZE GLOBAL MQTT CONNECTION
    // =====================================================
    private fun initGlobalMqtt() {
        // Teks inisial sebelum terhubung
        tvStatusKoneksi.text = "Status koneksi device: Menghubungkan..."
        tvStatusKoneksi.setTextColor(getColor(android.R.color.darker_gray))

        mqttManager.onConnected = {
            mqttManager.subscribe(TOPIC_SMALE)
            mqttManager.subscribe(TOPIC_AI)

            runOnUiThread {
                tvStatusKoneksi.text = "Status koneksi device: Tersambung"
                tvStatusKoneksi.setTextColor(getColor(android.R.color.holo_green_dark))
            }
        }

        mqttManager.onMessage = { topic, message ->
            if (topic == TOPIC_AI) {
                try {
                    val json = JSONObject(message)
                    val berat = json.getDouble("berat").toFloat()
                    val tinggi = json.getDouble("tinggi").toFloat()
                    val lemak = json.getDouble("bmi").toFloat()
                    val statusStunting = json.getString("stunting_status")
                    val weightPattern = json.getString("weight_pattern")
                    val rawPredHeight = json.getString("predicted_height")

                    runOnUiThread {
                        tvBerat.text = String.format("%.2f kg", berat)
                        tvTinggi.text = String.format("%.2f cm", tinggi)
                        tvLemak.text = String.format("%.2f %%", lemak)
                        tvStatus.text = "Status: $statusStunting"
                        tvAiBerat.text = weightPattern

                        if (rawPredHeight == "waiting_sequence") {
                            tvAiTinggi.text = "Menunggu antrean..."
                        } else {
                            tvAiTinggi.text = "$rawPredHeight cm"
                        }

                        val beratSekarang = tvBerat.text.toString().replace(" kg", "").toFloatOrNull() ?: 0f
                        val tinggiSekarang = tvTinggi.text.toString().replace(" cm", "").toFloatOrNull() ?: 0f

                        simpanRiwayatPengukuran(
                            beratSekarang,
                            tinggiSekarang,
                            lemak,
                            statusStunting,
                            rawPredHeight,
                            weightPattern
                        )

                        if (loadingDialog.isShowing) {
                            loadingDialog.dismiss()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MQTT_AI", "ERROR PARSING AI SERVER: ${e.message}")
                    runOnUiThread {
                        if (loadingDialog.isShowing) loadingDialog.dismiss()
                    }
                }
            }
        }

        // Coba hubungkan ke broker langsung saat halaman beranda dimuat
        mqttManager.connect()
    }

    // =====================================================
    // START MEASUREMENT (HANYA TRIGGERS DATA PUBLISH)
    // =====================================================
    private fun startMeasurement() {
        val child = getSelectedChild()
        if (child == null) {
            Toast.makeText(this, "Silakan pilih data anak terlebih dahulu", Toast.LENGTH_SHORT).show()
            return
        }

        loadingDialog.show()

        // Ambil data kirim ke hardware via MQTT broker
        val json = JSONObject().apply {
            put("id", child.getString("id"))
            put("gender", child.optString("jenis_kelamin", "-"))
            put("usia", child.getInt("usia"))
            put("wilayah", child.getString("wilayah"))
        }

        try {
            mqttManager.publish("smale/app/input", json.toString())
            Log.d("MQTT_SEND", json.toString())
        } catch (e: Exception) {
            Log.e("MQTT_PUBLISH_ERR", "Gagal Publish, mencoba hubungkan kembali...")
            if (loadingDialog.isShowing) loadingDialog.dismiss()

            // Jika seandainya putus di tengah jalan, trigger rekoneksi
            tvStatusKoneksi.text = "Status koneksi device: Terputus"
            tvStatusKoneksi.setTextColor(getColor(android.R.color.holo_red_dark))
            mqttManager.connect()
        }
    }

    // =====================================================
    // SIMPAN RIWAYAT
    // =====================================================
    private fun simpanRiwayatPengukuran(
        berat: Float,
        tinggi: Float,
        lemak: Float,
        status: String,
        predHeight: String = "-",
        weightPattern: String = "-"
    ) {
        val child = getSelectedChild() ?: return
        val pref = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val oldData = pref.getString(KEY_MEASUREMENT_HISTORY, null)
        val jsonArray = if (oldData != null) JSONArray(oldData) else JSONArray()

        val item = JSONObject().apply {
            put("nama", child.getString("nama"))
            put("usia", child.getInt("usia"))
            put("berat", berat)
            put("tinggi", tinggi)
            put("bmi", lemak)
            put("status", status)
            put("pred_height", predHeight)
            put("weight_pattern", weightPattern)
            put("timestamp", System.currentTimeMillis())
        }

        jsonArray.put(item)
        pref.edit().putString(KEY_MEASUREMENT_HISTORY, jsonArray.toString()).apply()
    }

    // =====================================================
    // LOADING
    // =====================================================
    private fun setupLoading() {
        loadingDialog = Dialog(this)
        loadingDialog.setContentView(R.layout.activity_loading)
        loadingDialog.setCancelable(true)

        loadingDialog.setOnCancelListener {
            Toast.makeText(this, "Pengukuran dibatalkan", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mqttManager.disconnect()
    }
}