package com.example.smale

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.io.ByteArrayOutputStream

class Profil : AppCompatActivity() {

    private lateinit var imgProfile: ImageView
    private lateinit var edtNama: EditText
    private lateinit var edtEmail: EditText
    private lateinit var edtRole: EditText
    private lateinit var edtPhone: EditText
    private lateinit var edtWifiSSID: EditText
    private lateinit var edtWifiPassword: EditText
    private lateinit var btnKirimWifi: Button

    private lateinit var btnEditNama: ImageView
    private lateinit var btnEditEmail: ImageView
    private lateinit var btnEditRole: ImageView
    private lateinit var btnEditPhone: ImageView

    private val PREF = "user_profile"

    companion object {
        const val PICK_IMAGE = 2001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profil)

        bindView()
        loadData()
        setupNavigation()

        // EDIT BUTTON
        btnEditNama.setOnClickListener { toggleEdit(edtNama) }
        btnEditEmail.setOnClickListener { toggleEdit(edtEmail) }
        btnEditRole.setOnClickListener { toggleEdit(edtRole) }
        btnEditPhone.setOnClickListener { toggleEdit(edtPhone) }

        // FOTO
        imgProfile.setOnClickListener { pickImage() }

        // TOMBOL WIFI
        btnKirimWifi.setOnClickListener { sendWifiConfig() }

        setupAutoSave()
    }

    private fun bindView() {
        imgProfile = findViewById(R.id.imgProfileUser)
        edtNama = findViewById(R.id.edtNama)
        edtEmail = findViewById(R.id.edtEmail)
        edtRole = findViewById(R.id.edtRole)
        edtPhone = findViewById(R.id.edtPhone)

        edtWifiSSID = findViewById(R.id.edtWifiSSID)
        edtWifiPassword = findViewById(R.id.edtWifiPassword)
        btnKirimWifi = findViewById(R.id.btnKirimWifi)

        btnEditNama = findViewById(R.id.btnEditNama)
        btnEditEmail = findViewById(R.id.btnEditEmail)
        btnEditRole = findViewById(R.id.btnEditRole)
        btnEditPhone = findViewById(R.id.btnEditPhone)
    }

    private fun sendWifiConfig() {
        val ssid = edtWifiSSID.text.toString().trim()
        val pass = edtWifiPassword.text.toString().trim()

        if (ssid.isEmpty() || pass.isEmpty()) {
            Toast.makeText(this, "SSID dan Password harus diisi", Toast.LENGTH_SHORT).show()
            return
        }

        // TAMBAHKAN "cmd":"WIFI_UPDATE" agar sesuai dengan pengecekan Arduino
        val jsonPayload = "{\"cmd\":\"WIFI_UPDATE\", \"ssid\":\"$ssid\", \"password\":\"$pass\"}"
        val topic = "smale/device/control" // Pastikan sama dengan TOPIC_CONTROL di Arduino

        try {
            // mqttClient.publish(topic, jsonPayload.toByteArray(), 0, false)
            Toast.makeText(this, "Konfigurasi WiFi berhasil dikirim!", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadData() {
        val pref = getSharedPreferences(PREF, Context.MODE_PRIVATE)
        edtNama.setText(pref.getString("nama", ""))
        edtEmail.setText(pref.getString("email", ""))
        edtRole.setText(pref.getString("role", ""))
        edtPhone.setText(pref.getString("phone", ""))

        val img = pref.getString("foto", null)
        if (!img.isNullOrEmpty()) {
            val bytes = Base64.decode(img, Base64.DEFAULT)
            imgProfile.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        }
    }

    private fun saveData() {
        getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString("nama", edtNama.text.toString())
            .putString("email", edtEmail.text.toString())
            .putString("role", edtRole.text.toString())
            .putString("phone", edtPhone.text.toString())
            .apply()
    }

    private fun toggleEdit(editText: EditText) {
        editText.isEnabled = true
        editText.requestFocus()
        editText.postDelayed({
            editText.isEnabled = false
            saveData()
        }, 1200)
    }

    private fun setupAutoSave() {
        val listener = { saveData() }
        edtNama.setOnFocusChangeListener { _, _ -> listener() }
        edtEmail.setOnFocusChangeListener { _, _ -> listener() }
        edtRole.setOnFocusChangeListener { _, _ -> listener() }
        edtPhone.setOnFocusChangeListener { _, _ -> listener() }
    }

    private fun pickImage() {
        startActivityForResult(Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI), PICK_IMAGE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            imgProfile.setImageURI(uri)
            val base64 = convertToBase64(uri)
            getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString("foto", base64).apply()
        }
    }

    private fun convertToBase64(uri: Uri): String {
        return try {
            val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream)
            Base64.encodeToString(stream.toByteArray(), Base64.DEFAULT)
        } catch (e: Exception) { "" }
    }

    private fun setupNavigation() {
        findViewById<View>(R.id.navBeranda).setOnClickListener { startActivity(Intent(this, Beranda::class.java)) }
        findViewById<View>(R.id.navMakanan).setOnClickListener { startActivity(Intent(this, Makanan::class.java)) }
        findViewById<View>(R.id.navRiwayat).setOnClickListener { startActivity(Intent(this, Riwayat::class.java)) }
    }
}