import pandas as pd
import numpy as np
import joblib
import os

from sklearn.preprocessing import MinMaxScaler
from sklearn.metrics import mean_absolute_error, mean_squared_error, r2_score
from tensorflow.keras.models import Sequential
from tensorflow.keras.layers import LSTM, Dense

# =====================================================
# 1. CONFIG
# =====================================================
USE_REAL_DATA = True  # <- nanti tinggal False kalau mau dummy

# =====================================================
# 2. LOAD DATA (SAFE)
# =====================================================
df_raw = pd.read_excel("Overall Data_Indo.xlsx")

df_raw.columns = df_raw.columns.str.strip()

def get_col(df, key):
    cols = [c for c in df.columns if key.lower() in c.lower()]
    return cols[0] if cols else None

age_col = get_col(df_raw, "age")
height_col = get_col(df_raw, "height")
weight_col = get_col(df_raw, "weight")

if not age_col or not height_col or not weight_col:
    raise ValueError("Kolom Age/Height/Weight tidak ditemukan!")

df_raw = df_raw[[age_col, height_col, weight_col]]
df_raw.columns = ["Age", "Height", "Weight"]

# =====================================================
# 3. CLEANING FIX (INI PENYEBAB ERROR KAMU)
# =====================================================
for col in ["Age", "Height", "Weight"]:
    df_raw[col] = pd.to_numeric(df_raw[col], errors="coerce")

df_raw = df_raw.dropna()

# safety check
if len(df_raw) < 10:
    raise ValueError("Data terlalu sedikit!")

# =====================================================
# 4. TIME SERIES SYNTHETIC GROWTH (FORECASTING BASE)
# =====================================================
dataset = []
np.random.seed(42)

for i, row in df_raw.iterrows():

    base_age = float(row["Age"])
    base_h = float(row["Height"])
    base_w = float(row["Weight"])

    h_growth = np.random.normal(0.25, 0.05)
    w_growth = np.random.normal(0.18, 0.03)

    for t in range(12):  # 12 bulan

        dataset.append([
            base_age + t/12,
            base_h + h_growth * t,
            base_w + w_growth * t
        ])

df = pd.DataFrame(dataset, columns=["Age", "Height", "Weight"])

# =====================================================
# 5. NORMALIZATION
# =====================================================
scaler = MinMaxScaler()
data_scaled = scaler.fit_transform(df)

# =====================================================
# 6. SEQUENCE DATA (LSTM)
# =====================================================
SEQ_LEN = 5

X, y = [], []

for i in range(len(data_scaled) - SEQ_LEN):
    X.append(data_scaled[i:i+SEQ_LEN])
    y.append(data_scaled[i+SEQ_LEN, 1:3])  # Height & Weight

X = np.array(X)
y = np.array(y)

# =====================================================
# 7. TRAIN TEST SPLIT
# =====================================================
split = int(len(X) * 0.8)

X_train, X_test = X[:split], X[split:]
y_train, y_test = y[:split], y[split:]

# =====================================================
# 8. MODEL LSTM
# =====================================================
model = Sequential([
    LSTM(64, return_sequences=True, input_shape=(SEQ_LEN, 3)),
    LSTM(32),
    Dense(2)
])

model.compile(optimizer="adam", loss="mse")

# =====================================================
# 9. TRAIN
# =====================================================
model.fit(
    X_train, y_train,
    epochs=25,
    batch_size=16,
    validation_data=(X_test, y_test),
    verbose=1
)

# =====================================================
# 10. EVALUATION (REAL METRICS)
# =====================================================
pred = model.predict(X_test)

mae = mean_absolute_error(y_test, pred)
rmse = np.sqrt(mean_squared_error(y_test, pred))
r2 = r2_score(y_test, pred)

print("\n================ EVALUATION ================")
print("MAE :", mae)
print("RMSE:", rmse)
print("R2  :", r2)

# =====================================================
# 11. SAVE MODEL
# =====================================================
os.makedirs("models", exist_ok=True)

model.save("models/lstm_model.h5")
joblib.dump(scaler, "models/scaler.pkl")

print("\nMODEL SAVED → models/")