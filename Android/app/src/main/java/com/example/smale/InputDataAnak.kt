package com.example.smale

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.*

class InputDataAnak : AppCompatActivity() {

    private lateinit var etNama: EditText
    private lateinit var etUsia: EditText
    private lateinit var rgGender: RadioGroup
    private lateinit var btnSimpan: TextView
    private lateinit var imgFoto: ImageView
    private lateinit var etWilayah: EditText

    private val PREF_NAME = "child_pref"
    private val KEY_CHILD_LIST = "child_list"
    private val KEY_SELECTED_CHILD = "selected_child"
    private val KEY_PENDING_UPLOAD = "pending_upload"

    private lateinit var mqttManager: MqttManager

    private val TOPIC_UPLOAD = "smale/app/child"

    private var fotoBase64 = ""

    companion object {
        const val PICK_IMAGE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_input_data_anak)

        mqttManager = MqttManager()

        bindView()

        imgFoto.setOnClickListener { pilihFoto() }
        btnSimpan.setOnClickListener { tambahDataAnak() }

        setupMQTT()
    }

    // ================= MQTT =================
    private fun setupMQTT() {

        mqttManager.onConnected = {
            kirimPendingJikaAda()
            toast("MQTT Connected")
        }

        mqttManager.connect()
    }

    // ================= SIMPAN DATA =================
    private fun tambahDataAnak() {

        val nama = etNama.text.toString().trim()
        val usiaText = etUsia.text.toString().trim()
        val wilayah = etWilayah.text.toString().trim()

        val selectedId = rgGender.checkedRadioButtonId

        if (nama.isEmpty() || usiaText.isEmpty() || selectedId == -1 || wilayah.isEmpty()) {
            toast("Lengkapi data")
            return
        }

        val usia = usiaText.toIntOrNull() ?: return
        val gender = findViewById<RadioButton>(selectedId).text.toString()

        // ================= JSON (FOTO HANYA LOCAL) =================
        val json = JSONObject().apply {

            put("id", generateShortId(6))
            put("nama", nama)
            put("usia", usia)
            put("jenis_kelamin", gender)
            put("wilayah", wilayah)

            // FOTO TIDAK MASUK MQTT
            put("foto", fotoBase64)

            put("created_at", System.currentTimeMillis())
        }

        // SIMPAN LOCAL
        simpanKePreferences(json)

        // ================= MQTT PAYLOAD (NO FOTO) =================
        val mqttJson = JSONObject().apply {
            put("id", json.getString("id"))
            put("nama", nama)
            put("usia", usia)
            put("jenis_kelamin", gender)
            put("wilayah", wilayah)
            put("created_at", System.currentTimeMillis())
        }

        val success = mqttManager.publish(TOPIC_UPLOAD, mqttJson.toString())

        if (!success) {
            simpanPending(mqttJson)
            toast("Offline → masuk pending")
        } else {
            toast("Terkirim ke MQTT")
        }

        setResult(RESULT_OK)
        finish()
    }

    // ================= PENDING =================
    private fun simpanPending(json: JSONObject) {

        val pref = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        val old = pref.getString(KEY_PENDING_UPLOAD, null)
        val arr = if (old != null) JSONArray(old) else JSONArray()

        arr.put(json)

        pref.edit()
            .putString(KEY_PENDING_UPLOAD, arr.toString())
            .apply()
    }

    private fun kirimPendingJikaAda() {

        val pref = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        val old = pref.getString(KEY_PENDING_UPLOAD, null) ?: return
        val arr = JSONArray(old)

        val sisa = JSONArray()

        for (i in 0 until arr.length()) {

            val item = arr.getJSONObject(i)

            val success = mqttManager.publish(
                TOPIC_UPLOAD,
                item.toString()
            )

            if (!success) {
                sisa.put(item)
            }
        }

        pref.edit()
            .putString(KEY_PENDING_UPLOAD, sisa.toString())
            .apply()
    }

    private fun generateShortId(length: Int = 6): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..length)
            .map { chars.random() }
            .joinToString("")
    }

    // ================= LOCAL SAVE =================
    private fun simpanKePreferences(json: JSONObject) {

        val pref = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        val old = pref.getString(KEY_CHILD_LIST, null)
        val arr = if (old != null) JSONArray(old) else JSONArray()

        arr.put(json)

        pref.edit()
            .putString(KEY_CHILD_LIST, arr.toString())
            .putString(KEY_SELECTED_CHILD, json.toString())
            .apply()
    }

    // ================= IMAGE PICK =================
    private fun pilihFoto() {

        val intent = Intent(
            Intent.ACTION_PICK,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        )

        startActivityForResult(intent, PICK_IMAGE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_IMAGE && resultCode == RESULT_OK) {

            val uri = data?.data
            imgFoto.setImageURI(uri)

            uri?.let {
                fotoBase64 = convertImageToBase64(it)
            }
        }
    }

    private fun convertImageToBase64(uri: Uri): String {

        return try {

            val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)

            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream)

            Base64.encodeToString(stream.toByteArray(), Base64.DEFAULT)

        } catch (e: Exception) {
            ""
        }
    }

    // ================= UI =================
    private fun bindView() {

        etNama = findViewById(R.id.etNama)
        etUsia = findViewById(R.id.etUsia)
        rgGender = findViewById(R.id.rgGender)
        etWilayah = findViewById(R.id.etWilayah)

        btnSimpan = findViewById(R.id.btnSaveChild)
        imgFoto = findViewById(R.id.imgFotoAnak)
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}