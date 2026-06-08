const mqtt = require("mqtt");
const mysql = require("mysql2");

// =====================================================
// MQTT CONNECT
// =====================================================
const client = mqtt.connect("mqtt://103.172.204.106:1883");

// =====================================================
// MYSQL POOL CONNECT
// =====================================================
const db = mysql.createPool({
  host: "103.172.204.106",
  user: "admin",
  password: "TekkomB22",
  database: "smale_db",
  waitForConnections: true,
  connectionLimit: 10,
  queueLimit: 0
});

// =====================================================
// TEST MYSQL CONNECTION
// =====================================================
db.getConnection((err, connection) => {
  if (err) {
    console.log("MYSQL ERROR:", err.message);
  } else {
    console.log("MySQL Pool Connected");
    connection.release();
  }
});

// =====================================================
// TOPICS
// =====================================================
const TOPIC_CHILD = "smale/app/child";
const TOPIC_MAKANAN = "smale/app/makanan";
const TOPIC_RESULT = "smale/server/result";

// =====================================================
// MQTT CONNECT EVENT
// =====================================================
client.on("connect", () => {
  console.log("MQTT Connected");

  client.subscribe([
    TOPIC_CHILD,
    TOPIC_MAKANAN,
    TOPIC_RESULT
  ]);

  console.log("Subscribed to topics");
});

// =====================================================
// MQTT MESSAGE HANDLER
// =====================================================
client.on("message", (topic, message) => {

  try {

    const data = JSON.parse(message.toString());

    console.log("\n========================");
    console.log("TOPIC :", topic);
    console.log("DATA  :", data);
    console.log("========================");

    // =================================================
    // DATA ANAK
    // =================================================
    if (topic === TOPIC_CHILD) {

      const id_anak =
        data.id ||
        data.id_anak ||
        data.child_id ||
        null;

      const nama =
        data.nama ||
        null;

      const umur =
        data.usia ||
        data.umur ||
        data.age ||
        null;

      const jenis_kelamin =
        data.jenis_kelamin ||
        data.gender ||
        null;

      const wilayah =
        data.wilayah ||
        null;

      db.query(
        `
        INSERT INTO data_anak
        (
          id_anak,
          nama,
          umur,
          jenis_kelamin,
          wilayah
        )
        VALUES (?, ?, ?, ?, ?)
        `,
        [
          id_anak,
          nama,
          umur,
          jenis_kelamin,
          wilayah
        ],
        (err) => {
          if (err) {
            console.log("DB ERROR DATA ANAK:", err.message);
          } else {
            console.log("DATA ANAK SAVED TO MYSQL");
          }
        }
      );
    }

    // =================================================
    // MONITORING RESULT
    // =================================================
    if (topic === TOPIC_RESULT) {

      const id_anak =
        data.id_anak ||
        data.child_id ||
        null;

      const id_device =
        data.id_device ||
        "DEV-SMALE01";

      const umur =
        data.umur ||
        data.age ||
        null;

      const jenis_kelamin =
        data.jenis_kelamin ||
        data.gender ||
        null;

      const wilayah =
        data.wilayah ||
        null;

      const tinggi =
        data.tinggi ||
        data.height ||
        null;

      const berat =
        data.berat ||
        data.weight ||
        null;

      const bmi =
        data.bmi ||
        null;

      const status =
        data.status ||
        data.stunting_status ||
        null;

      // =================================================
      // HANDLE predicted_height
      // =================================================

      let prediksi_tinggi =
        data.prediksi_tinggi ??
        data.predicted_height ??
        data.predict_height;

      prediksi_tinggi = parseFloat(prediksi_tinggi);

      if (isNaN(prediksi_tinggi)) {
        prediksi_tinggi = 0;
      }

      // =================================================
      // HANDLE weight_pattern
      // =================================================

      const pola_berat =
        data.pola_berat ||
        data.weight_pattern ||
        data.status_weight ||
        null;

      db.query(
        `
        INSERT INTO monitoring_anak
        (
          id_anak,
          id_device,
          umur,
          jenis_kelamin,
          wilayah,
          tinggi,
          berat,
          bmi,
          status,
          prediksi_tinggi,
          pola_berat
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        `,
        [
          id_anak,
          id_device,
          umur,
          jenis_kelamin,
          wilayah,
          tinggi,
          berat,
          bmi,
          status,
          prediksi_tinggi,
          pola_berat
        ],
        (err) => {
          if (err) {
            console.log("DB ERROR MONITORING:", err.message);
          } else {
            console.log("MONITORING DATA SAVED TO MYSQL");
          }
        }
      );
    }

  } catch (err) {

    console.log("PARSE ERROR:", err.message);

  }

});

// =====================================================
// MQTT ERROR
// =====================================================
client.on("error", (err) => {
  console.log("MQTT ERROR:", err.message);
});