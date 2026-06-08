import os
os.environ['TF_CPP_MIN_LOG_LEVEL'] = '2'

import json
import numpy as np
import pandas as pd
import joblib
import paho.mqtt.client as mqtt
from tensorflow.keras.models import load_model

# =========================================================
# LOAD MODEL
# =========================================================

print("Loading models...")

height_model = load_model("models/height_lstm_model.h5", compile=False)
height_scaler = joblib.load("models/height_scaler.save")
weight_model = joblib.load("models/weight_model.pkl")

print("Models loaded!")

# =========================================================
# WHO DATASET (DIPERBAIKI)
# =========================================================

# Ubah definisi fungsi Anda menjadi seperti ini:
def load_who_data(file_path):
    # Baca file tanpa header untuk menemukan baris header yang benar
    df_raw = pd.read_excel(file_path, header=None, dtype=str)
    
    # Cari baris yang mengandung 'Months' atau 'Month' (case-insensitive)
    mask = df_raw.apply(lambda row: row.astype(str).str.contains('Month', case=False).any(), axis=1)
    header_idx = mask[mask].index[0]
    
    # Baca ulang dengan header yang benar
    df = pd.read_excel(file_path, header=header_idx)
    df.columns = df.columns.astype(str).str.strip()
    
    # Standardisasi nama kolom Months
    if 'Month' in df.columns: df = df.rename(columns={'Month': 'Months'})
    if 'Months' not in df.columns: df = df.rename(columns={df.columns[1]: 'Months'})
    
    df["Months"] = pd.to_numeric(df["Months"], errors="coerce")
    return df.dropna(subset=["Months"])

print("Loading WHO datasets...")

# DATASET DI BAWAH 5 TAHUN (0-60 BULAN)
boys_05 = load_who_data("datasets/lhfa_boys_2-to-5-years_zscores.xlsx")
girls_05 = load_who_data("datasets/lhfa_girls_2-to-5-years_zscores.xlsx")

# DATASET DI ATAS 5 TAHUN (61-228 BULAN)
boys_519 = load_who_data("datasets/sft-hfa-boys-z-5-19years.xlsx")
girls_519 = load_who_data("datasets/sft-hfa-girls-z-5-19years.xlsx")

print("All WHO datasets loaded successfully!")

print("Dataset loaded successfully!")
# =========================================================
# WHO STUNTING
# =========================================================

def get_stunting_status(age, gender, height):
    try:
        age, gender = int(age), str(gender).lower().strip()
        
        # Pilih dataset berdasarkan usia
        if age <= 60:
            df = boys_05 if "male" in gender else girls_05
            # Dataset < 5 tahun biasanya menggunakan nama kolom "SD3neg"
            col_sd3, col_sd2 = "SD3neg", "SD2neg"
        else:
            df = boys_519 if "male" in gender else girls_519
            # Dataset > 5 tahun menggunakan nama kolom "-3 SD"
            col_sd3, col_sd2 = "-3 SD", "-2 SD"

        # Cari baris usia terdekat
        df['diff'] = abs(df['Months'] - age)
        row = df.loc[df['diff'] == df['diff'].min()].iloc[[0]]
        
        sd3 = float(row[col_sd3].iloc[0])
        sd2 = float(row[col_sd2].iloc[0])

        if height < sd3: return "SEVERE STUNTING"
        elif height < sd2: return "STUNTING"
        else: return "NORMAL"
        
    except Exception as e:
        print(f"[DEBUG ERROR WHO STUNTING]: {e}")
        return "UNKNOWN"
# =========================================================
# MQTT CONFIG
# =========================================================

BROKER = "103.172.204.106"
PORT = 1883

TOPIC_SUB = "smale/device/result"
TOPIC_PUB = "smale/server/result"

client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION1)

# =========================================================
# BUFFER LSTM
# =========================================================

sequence_buffer = []
SEQ_LEN = 8

# =========================================================
# CONNECT
# =========================================================

def on_connect(client, userdata, flags, rc):
    print(f"Connected MQTT Broker with result code: {rc}")
    client.subscribe(TOPIC_SUB)
    print(f"Successfully subscribed to topic: {TOPIC_SUB}")

# =========================================================
# MESSAGE HANDLER
# =========================================================

def on_message(client, userdata, msg):

    global sequence_buffer

    try:
        payload = json.loads(msg.payload.decode())

        # =================================================
        # VISUAL DEBUGGING: DATA MASUK DARI MQTT
        # =================================================
        print("\n" + "="*60)
        print("📥 [LOG DATA MASUK DARI DEVICE / CMD]")
        print("="*60)
        print(json.dumps(payload, indent=4))
        print("-"*60)

    
        # Pastikan kunci JSON sesuai dengan yang dikirim device (perhatikan huruf besar/kecil)
        age = float(payload.get("umur", payload.get("usia", payload.get("Age", 0))))
        height = float(payload.get("tinggi", payload.get("height", payload.get("Height", 0))))
        weight = float(payload.get("berat", payload.get("weight", payload.get("Weight", 0))))
        gender = payload.get("jenis_kelamin", payload.get("gender", "male"))
        print(f"DEBUG DATA -> Usia: {age}, Tinggi: {height}, Berat: {weight}, Gender: {gender}")
        # =================================================
        # VALIDASI DASAR
        # =================================================

        if height <= 0 or weight <= 0:
            print("⚠️ [WARN] Invalid data: Tinggi atau berat badan bernilai 0/negatif.")
            return

        # =================================================
        # BMI
        # =================================================

        bmi = weight / ((height / 100) ** 2)
        print(f"📊 [CALCULATION] Nilai Kalkulasi BMI: {round(bmi, 4)}")

        # =================================================
        # WHO STUNTING
        # =================================================

        gender = payload.get(
            "jenis_kelamin",
            payload.get("gender", "male")
        )
        stunting_status = get_stunting_status(
            age,
            gender,
            height
        )
        print(f"📋 [WHO LOOKUP] Status Stunting Berdasarkan Tabel WHO: {stunting_status}")

        # =================================================
        # LSTM INPUT (PAKAI FIELD DARI DEVICE)
        # =================================================

        input_df = pd.DataFrame([[
            age, height, weight, bmi
        ]], columns=["Age", "Height", "Weight", "BMI"])

        scaled = height_scaler.transform(input_df)

        sequence_buffer.append(scaled[0])
        if len(sequence_buffer) > SEQ_LEN:
            sequence_buffer.pop(0)

        # VISUAL DEBUGGING: Cek status array runtun waktu untuk model LSTM
        print(f"🔄 [DEBUG MODEL LSTM] Ukuran Antrean Buffer: {len(sequence_buffer)}/{SEQ_LEN}")

        predicted_height = "waiting_sequence"

        if len(sequence_buffer) == SEQ_LEN:

            lstm_input = np.array([sequence_buffer])
            pred_scaled = height_model.predict(lstm_input, verbose=0)

            temp = np.zeros((1, 4))
            temp[0][1] = pred_scaled[0][0]

            inverse = height_scaler.inverse_transform(temp)

            predicted_height = round(float(inverse[0][1]), 2)
            print(f"✨ [DEBUG MODEL LSTM] Prediksi Tinggi Badan Selanjutnya: {predicted_height} cm")
        else:
            print(f"⏳ [DEBUG MODEL LSTM] Prediksi Ditunda. Butuh {SEQ_LEN - len(sequence_buffer)} data berurutan lagi.")

        # =================================================
        # RANDOM FOREST (WEIGHT PATTERN) - AUTO MATCH ORDER
        # =================================================

        # 1. Konversi teks gender menjadi numerik (male -> 1, female -> 0)
        gender_encoded = 1 if gender.lower() == "male" else 0

        raw_features = {
            "Age": float(age), 
            "Height": float(height),
            "Gender": int(gender_encoded)
        }

        # 3. Cek otomatis urutan kolom asli dari dalam model pkl Anda
        if hasattr(weight_model, "feature_names_in_"):
            model_features = weight_model.feature_names_in_
            # Susun data berdasarkan urutan persis yang diminta oleh model
            rf_data = [[raw_features[feat] for feat in model_features]]
            rf_input = pd.DataFrame(rf_data, columns=model_features)
        else:
            # Jika properti tidak ketemu, fallback ke susunan standar sebelumnya
            rf_input = pd.DataFrame([[
                age, height, gender_encoded
            ]], columns=["Age", "Height", "Gender"])

        # 4. Eksekusi Prediksi
        score = float(weight_model.predict(rf_input)[0])

        # VISUAL DEBUGGING: Menampilkan struktur input dan output mentah model RF
        print("-"*60)
        print("🧠 [DEBUG MODEL RANDOM FOREST]")
        print(f"-> Kolom Fitur yang Digunakan: {rf_input.columns.tolist()}")
        print(f"-> Nilai Array Input ke Model: {rf_input.values.tolist()[0]}")
        print(f"-> Nilai Raw Output (Score) dari Model RF: {score}")

        # =================================================
        # INTERPRETASI STATUS GIZI (BB/U) BERDASARKAN PREDIKSI BERAT RF
       # 1. Age Clamping: Jika umur > 60 bulan, kita gunakan 60 agar model tetap stabil
        # Namun, kita tetap menyimpan 'age' asli untuk logika kondisi di bawah
        model_age = age if age <= 60 else 60
        
        # Jika Anda ingin prediksi tetap akurat berdasarkan umur asli (tapi model RF Anda tidak bisa),
        # teknik di atas memaksa model memberi output yang masuk akal bagi balita.
        
        # =================================================
        # INTERPRETASI STATUS GIZI (BB/U)
        # =================================================
        
        if age > 60:
            # UNTUK REMAJA: Tidak butuh score dari model RF
            score = 0.0 # Placeholder
            selisih_berat = 0.0
            
            if bmi < 17: weight_pattern = "GIZI KURANG"
            elif 17 <= bmi <= 23: weight_pattern = "GIZI BAIK (NORMAL)"
            elif 23 < bmi <= 27: weight_pattern = "BERAT BADAN LEBIH"
            else: weight_pattern = "OBESITAS"
            
            print(f"-> [LOG INFO] Usia > 60: Menggunakan logika BMI.")

        else:
            # UNTUK BALITA: Wajib ambil score dari model RF
            score = float(weight_model.predict(rf_input)[0])
            selisih_berat = weight - score
            
            if selisih_berat < -3.0: weight_pattern = "GIZI BURUK"
            elif -3.0 <= selisih_berat < -2.0: weight_pattern = "GIZI KURANG"
            elif -2.0 <= selisih_berat <= 7.5: weight_pattern = "GIZI BAIK (NORMAL)"
            elif 7.5 < selisih_berat <= 10.0: weight_pattern = "BERAT BADAN LEBIH"
            else: weight_pattern = "OBESITAS"

            # --- TAMBAHKAN LOGIKA PENGUNCI (INTEGRASI STUNTING) ---
            if stunting_status == "SEVERE STUNTING" and weight_pattern == "GIZI BAIK (NORMAL)":
                weight_pattern = "GIZI KURANG (KRITIS)"
                print("-> [LOG WARNING] Anak Severe Stunting terdeteksi dengan BB proporsional.")
            
            elif stunting_status == "STUNTING" and weight_pattern == "GIZI BAIK (NORMAL)":
                weight_pattern = "GIZI KURANG"
            
            print(f"-> [LOG AJUSTMENT] Selisih Berat: {round(selisih_berat, 2)} kg")
        print(f"-> Hasil Konversi Label Akhir: {weight_pattern}")

        # =================================================
        # GABUNGKAN OUTPUT + DATA ORIGINAL
        # =================================================

        result = payload.copy()

        result.update({
            "bmi": round(float(bmi), 2),
            "stunting_status": stunting_status,
            "predicted_height": predicted_height,
            "weight_pattern": weight_pattern
        })

        # =================================================
        # PUBLISH & VISUAL DEBUGGING OUTPUT AKHIR
        # =================================================

        print("📤 [LOG FINAL RESULT YANG DIKIRIM KE SERVER]")
        print(json.dumps(result, indent=4))
        print("="*60 + "\n")

        client.publish(TOPIC_PUB, json.dumps(result))

    except Exception as e:
        print("🚨 [CRITICAL LOG ERROR]:", e)

# =========================================================
# MQTT SETUP
# =========================================================

client.on_connect = on_connect
client.on_message = on_message

print("Connecting MQTT...")

client.connect(BROKER, PORT, 60)
client.loop_forever()