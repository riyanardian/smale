#include <WiFi.h>
#include <PubSubClient.h>
#include <Arduino_GFX_Library.h>
#include <SPI.h>
#include <HX711_ADC.h>
#include <Preferences.h>

// ======================================================
// CONFIGURATION PIN TFT & SENSOR
// ======================================================
#define TFT_CS     14
#define TFT_DC     26
#define TFT_RST    5
#define TFT_SCK    18
#define TFT_MISO   19
#define TFT_MOSI   23

#define HX_DOUT    17
#define HX_SCK     16
#define TRIG_PIN   33
#define ECHO_PIN   32

#define SENSOR_OFFSET_CM 5.0
const float MY_CAL_FACTOR = 23368.10;

// ======================================================
// TFT INITIALIZATION
// ======================================================
Arduino_DataBus *bus = new Arduino_ESP32SPI(TFT_DC, TFT_CS, TFT_SCK, TFT_MOSI, TFT_MISO);
Arduino_GFX *gfx = new Arduino_ILI9486_18bit(bus, TFT_RST, 1, false);

// ======================================================
// NETWORK CREDENTIALS & STORAGE
// ======================================================
Preferences preferences;

char ssid[50] = "smale";       
char password[50] = "smale123"; 
const char* mqtt_server = "103.172.204.106"; 

WiFiClient espClient;
PubSubClient client(espClient);
HX711_ADC LoadCell(HX_DOUT, HX_SCK);

// ======================================================
// MQTT TOPICS
// ======================================================
const char* TOPIC_INPUT           = "smale/app/input";     
const char* TOPIC_RESULT_PUB      = "smale/device/result"; 
const char* TOPIC_SERVER_RESPONSE = "smale/server/result"; 
const char* TOPIC_CONTROL         = "smale/device/control"; 

// ======================================================
// MODERN & BALANCED COLOR PALETTE (Hex 565 RGB)
// ======================================================
#define BG_COLOR      0x0861
#define CARD_BG       0x10A2
#define CARD_BORDER   0x2965

#define TEXT_MAIN     0xFFFF
#define TEXT_MUTED    0xAD55

#define COLOR_ACCENT  0x05FF
#define COLOR_SUCCESS 0x07E0
#define COLOR_WARN    0xFD20
#define COLOR_DANGER  0xF800
// ======================================================
// FINITE STATE MACHINE (FSM) SCREEN SYSTEM
// ======================================================
enum ScreenState {
  BOOT_SCREEN,
  WIRING_SCREEN,
  RULES_SCREEN,
  SCAN_SCREEN,
  RESULT_SCREEN,
  WIFI_UPDATE_SCREEN,
  FINISH_SCREEN
};

ScreenState currentScreen = BOOT_SCREEN;
ScreenState lastScreen = BOOT_SCREEN;

// ======================================================
// GLOBAL VARIABLES
// ======================================================
bool mqttReady = false;
bool childDataReady = false;
bool tareRequest = false;
bool offlineMode = false;
bool needRedraw = true;
bool dataSent = false;
unsigned long connectionStartTime = 0;
String device_id = "DEV-SMALE01";
String child_id = "";
String child_gender = "";
int child_age = 0;
String child_wilayah = "";

float weight = 0;
float height = 0;
float bmi = 0;
String statusStunting = "Memuat...";

int countdown = 5;
unsigned long screenTimer = 0;
unsigned long lastCountdown = 0;
unsigned long lastSensorRead = 0;

float lastDispWeight = -1.0;
float lastDispHeight = -1.0;
float lastDispBmi = -1.0;
int lastDispCount = -1;
bool wasOffline = false;
unsigned long bootStartTime = 0;
float lastWeightStable = 0;
float lastHeightStable = 0;

int stableWeightCount = 0;
int stableHeightCount = 0;

const float WEIGHT_THRESHOLD = 0.2; // kg
const float HEIGHT_THRESHOLD = 1.0; // cm
const int REQUIRED_STABLE_READS = 10;
// ======================================================
// EXTRACTOR JSON MANUAL
// ======================================================
String getJson(String data, String key) {
  int start = data.indexOf("\"" + key + "\"");
  if (start == -1) return "";
  start = data.indexOf(":", start) + 1;
  int end = data.indexOf(",", start);
  if (end == -1) end = data.indexOf("}", start);
  String value = data.substring(start, end);
  value.replace("\"", "");
  value.trim();
  return value;
}

// ======================================================
// HARDWARE SENSOR READERS
// ======================================================
float readWeight() {
  static float lastWeight = 0;
  if (LoadCell.update()) {
    lastWeight = LoadCell.getData();
  }
  if (lastWeight < 0.1) lastWeight = 0.0; 
  return lastWeight;
}

float readHeight() {

  digitalWrite(TRIG_PIN, LOW);
  delayMicroseconds(2);

  digitalWrite(TRIG_PIN, HIGH);
  delayMicroseconds(10);

  digitalWrite(TRIG_PIN, LOW);

  long duration = pulseIn(ECHO_PIN, HIGH, 30000);

  if (duration == 0) {
    return height; // gunakan nilai terakhir jika gagal baca
  }

  float distance = duration * 0.0343 / 2.0;

  return distance + SENSOR_OFFSET_CM;
}

void readSensors() {
  weight = readWeight();
  height = readHeight();
  // ===== CEK STABIL BERAT =====
  if (weight > 5) {

    if (abs(weight - lastWeightStable) < WEIGHT_THRESHOLD) {
      stableWeightCount++;
    } else {
      stableWeightCount = 0;
    }

  }

  // ===== CEK STABIL TINGGI =====

  if (height > 50) {

    if (abs(height - lastHeightStable) < HEIGHT_THRESHOLD) {
      stableHeightCount++;
    } else {
      stableHeightCount = 0;
    }

  }

  lastWeightStable = weight;
  lastHeightStable = height;

  if (offlineMode) {
    float tinggiMeter = lastHeightStable / 100.0;
    if (tinggiMeter > 0) {
      bmi = lastWeightStable / (tinggiMeter * tinggiMeter);
    } else {
      bmi = 0;
    }
    statusStunting = "OFFLINE";
  }
}

// ======================================================
// MQTT INCOMING CALLBACK
// ======================================================
void callback(char* topic, byte* payload, unsigned int length) {
  String msg = "";
  for (int i = 0; i < length; i++) msg += (char)payload[i];

  Serial.print("Message arrived [");
  Serial.print(topic);
  Serial.print("] : ");
  Serial.println(msg);

 if (String(topic) == TOPIC_INPUT) {
    child_id      = getJson(msg, "id");
    
    String raw_gender = getJson(msg, "jenis_kelamin"); 
    if(raw_gender == "") raw_gender = getJson(msg, "gender");
    raw_gender.toLowerCase(); // Ubah ke huruf kecil semua biar gampang dicek
    
    if (raw_gender == "laki-laki" || raw_gender == "l" || raw_gender == "cowok" || raw_gender == "male") {
      child_gender = "Male";
    } else if (raw_gender == "perempuan" || raw_gender == "p" || raw_gender == "cewek" || raw_gender == "female") {
      child_gender = "Female";
    } else {
      child_gender = "Unknown"; // Fallback jika input tidak dikenali
    }
    
    child_age     = getJson(msg, "usia").toInt();
    child_wilayah = getJson(msg, "wilayah");
    
    childDataReady = true;
    Serial.println("Data anak tersinkronisasi. Gender: " + child_gender);
  }
  else if (String(topic) == TOPIC_SERVER_RESPONSE) {
    String server_bmi = getJson(msg, "bmi");
    String server_status = getJson(msg, "status");

    if (server_bmi != "") bmi = server_bmi.toFloat();
    if (server_status != "") statusStunting = server_status;

    Serial.println("Kalkulasi Server Berhasil Diterima.");
    
    // PENTING: Jika kita berada di layar hasil, paksa redraw
    if(currentScreen == RESULT_SCREEN) {
      needRedraw = true; 
    }
}
  else if (String(topic) == TOPIC_CONTROL) {
    String cmd = getJson(msg, "cmd");
    
    if (cmd == "WIFI_UPDATE") {
      String new_ssid = getJson(msg, "ssid");
      String new_pass = getJson(msg, "password");
      
      if(new_ssid != "" && new_pass != "") {
        new_ssid.toCharArray(ssid, 50);
        new_pass.toCharArray(password, 50);

        preferences.begin("smale_wifi", false);
        preferences.putString("ssid", new_ssid);
        preferences.putString("password", new_pass);
        preferences.end();
        
        Serial.println("Wi-Fi Baru Disimpan ke Flash Permanen!");

        currentScreen = WIFI_UPDATE_SCREEN;
        needRedraw = true;
        screenTimer = millis();
      }
    }
    else if (cmd == "TARE") {
      tareRequest = true;
    }
    else if (cmd == "RESET") {
      weight = 0; height = 0; bmi = 0;
      childDataReady = false;
      currentScreen = BOOT_SCREEN;
      screenTimer = millis();
    }
    else if (cmd == "REBOOT") {
      ESP.restart();
    }
  }
}

// ======================================================
// NETWORK MANAGEMENT
// ======================================================
void wifiReconnect() {
  if (WiFi.status() == WL_CONNECTED) return;
  static unsigned long lastConnectAttempt = 0;
  if (millis() - lastConnectAttempt < 6000) return;
  lastConnectAttempt = millis();

  Serial.println("Menghubungkan Wi-Fi...");
  WiFi.disconnect();
  WiFi.begin(ssid, password);
}

void reconnectMQTT() {
  if (WiFi.status() != WL_CONNECTED || client.connected()) return;

  static unsigned long lastMqttAttempt = 0;
  if (millis() - lastMqttAttempt < 4000) return;
  lastMqttAttempt = millis();

  String clientId = "SMALE_HARDWARE_" + String(random(0xffff), HEX);

  if (client.connect(clientId.c_str())) {
    client.subscribe(TOPIC_INPUT);
    client.subscribe(TOPIC_SERVER_RESPONSE);
    client.subscribe(TOPIC_CONTROL);

    mqttReady = true;
    offlineMode = false;

    Serial.println("MQTT Broker Connected.");
  } else {
    mqttReady = false;
  }
}

void checkConnectionMode() {

  bool connected =
      (WiFi.status() == WL_CONNECTED &&
       client.connected());

  if (connected) {

    mqttReady = true;

    if (wasOffline) {

      Serial.println("Koneksi kembali!");

      wasOffline = false;
      offlineMode = false;

      currentScreen = BOOT_SCREEN;
      screenTimer = millis();
      needRedraw = true;
    }

    connectionStartTime = millis();
  }
  else {

    mqttReady = false;

    if (millis() - connectionStartTime > 10000) {
      offlineMode = true;
      wasOffline = true;
    }
  }
}

// ======================================================
// PUBLISH DATA RAW TO SERVER NODE.JS
// ======================================================
void publishResult() {
  if(offlineMode) return;

  String json = "{"
    "\"id_device\":\"" + device_id + "\","
    "\"id_anak\":\"" + child_id + "\","
    "\"umur\":" + String(child_age) + ","
    "\"jenis_kelamin\":\"" + child_gender + "\","
    "\"wilayah\":\"" + child_wilayah + "\","
    "\"tinggi\":" + String(height, 1) + ","
    "\"berat\":" + String(weight, 1) +
    "}";

  client.publish(TOPIC_RESULT_PUB, json.c_str());
}

// ======================================================
// MODERN GRAPHICS INTERFACE CODES (UI RECONSTRUCTION)
// ======================================================

void drawBoot() {

  gfx->fillScreen(BG_COLOR);

  gfx->fillRoundRect(60, 40, 360, 140, 15, CARD_BG);
  gfx->drawRoundRect(60, 40, 360, 140, 15, CARD_BORDER);

  gfx->setTextColor(TEXT_MAIN);
  gfx->setTextSize(5);
  gfx->setCursor(120, 70);
  gfx->println("SMALE");

  gfx->setTextColor(COLOR_ACCENT);
  gfx->setTextSize(2);
  gfx->setCursor(110, 130);
  gfx->println("Smart Growth Monitoring");

  gfx->fillRoundRect(80, 230, 320, 18, 8, CARD_BG);
  gfx->fillRoundRect(80, 230, 180, 18, 8, COLOR_ACCENT);

  gfx->setTextColor(TEXT_MUTED);
  gfx->setTextSize(1);
  gfx->setCursor(185, 265);
  gfx->println("INITIALIZING...");
}

void drawWiring() {

  gfx->fillScreen(BG_COLOR);

  gfx->setTextColor(TEXT_MAIN);
  gfx->setTextSize(3);
  gfx->setCursor(120, 25);
  gfx->println("SYSTEM STATUS");

  gfx->fillRoundRect(40, 80, 400, 170, 12, CARD_BG);
  gfx->drawRoundRect(40, 80, 400, 170, 12, CARD_BORDER);

  if (!offlineMode) {

    gfx->fillCircle(90, 130, 18, COLOR_SUCCESS);

    gfx->setTextColor(COLOR_SUCCESS);
    gfx->setTextSize(2);
    gfx->setCursor(130, 120);
    gfx->println("ONLINE");

    gfx->setTextColor(TEXT_MUTED);
    gfx->setCursor(130, 160);
    gfx->println("MQTT Broker Connected");

    gfx->setCursor(130, 190);
    gfx->println("Waiting Child Data");
  }
  else {

    gfx->fillCircle(90, 130, 18, COLOR_WARN);

    gfx->setTextColor(COLOR_WARN);
    gfx->setCursor(130, 120);
    gfx->println("OFFLINE MODE");

    gfx->setTextColor(TEXT_MUTED);
    gfx->setCursor(130, 160);
    gfx->println("Running Standalone");

    gfx->setCursor(130, 190);
    gfx->println("Starting in 10 sec");
  }
}

void drawRules() {

  gfx->fillScreen(BG_COLOR);

  gfx->setTextColor(COLOR_ACCENT);
  gfx->setTextSize(3);
  gfx->setCursor(100, 30);
  gfx->println("SCAN PREPARATION");

  gfx->fillRoundRect(35, 80, 410, 180, 12, CARD_BG);

  gfx->setTextColor(TEXT_MAIN);
  gfx->setTextSize(2);

  gfx->setCursor(55, 105);
  gfx->println("1. Remove Footwear");

  gfx->setCursor(55, 145);
  gfx->println("2. Stand Upright");

  gfx->setCursor(55, 185);
  gfx->println("3. Look Forward");

  gfx->setCursor(55, 225);
  gfx->println("4. Stay Still");
}

void drawScanBase() {

  gfx->fillScreen(BG_COLOR);

  gfx->setTextColor(COLOR_ACCENT);
  gfx->setTextSize(3);
  gfx->setCursor(120, 20);
  gfx->println("MEASURING");

  gfx->fillRoundRect(20, 70, 220, 170, 10, CARD_BG);
  gfx->drawRoundRect(20, 70, 220, 170, 10, CARD_BORDER);

  gfx->setTextColor(TEXT_MUTED);
  gfx->setTextSize(2);

  gfx->setCursor(35, 95);
  gfx->println("Weight");

  gfx->setCursor(35, 150);
  gfx->println("Height");

  gfx->setCursor(35, 205);
  gfx->println("BMI");

  gfx->drawCircle(360, 160, 70, CARD_BORDER);
  gfx->drawCircle(360, 160, 71, CARD_BORDER);

  gfx->setTextColor(TEXT_MUTED);
  gfx->setTextSize(1);
  gfx->setCursor(285, 250);
  gfx->println("SCANNING...");
}

void drawScanValues() {

  if (weight != lastDispWeight) {

    gfx->fillRect(120, 90, 100, 30, CARD_BG);

    gfx->setTextColor(TEXT_MAIN);
    gfx->setTextSize(2);

    gfx->setCursor(120, 95);
    gfx->print(weight,1);
    gfx->print("kg");

    lastDispWeight = weight;
  }

  if (height != lastDispHeight) {

    gfx->fillRect(120, 145, 100, 30, CARD_BG);

    gfx->setCursor(120,150);
    gfx->print(height,1);
    gfx->print("cm");

    lastDispHeight = height;
  }

  if (bmi != lastDispBmi) {

    gfx->fillRect(120, 200, 100, 30, CARD_BG);

    gfx->setCursor(120,205);

    if(offlineMode)
      gfx->print(bmi,1);
    else
      gfx->print("--");

    lastDispBmi = bmi;
  }

  if (countdown != lastDispCount) {

    gfx->fillCircle(360,160,60,BG_COLOR);

    gfx->setTextColor(COLOR_ACCENT);
    gfx->setTextSize(10);

    if(countdown >= 10)
      gfx->setCursor(300,120);
    else
      gfx->setCursor(325,120);

    gfx->print(countdown);

    lastDispCount = countdown;
  }
}

void drawResult() {
  gfx->fillScreen(BG_COLOR);

  // Judul
  gfx->setTextColor(TEXT_MAIN);
  gfx->setTextSize(3);
  gfx->setCursor(90, 20);
  gfx->println("HASIL SCAN");

  gfx->drawFastHLine(40, 60, 400, COLOR_ACCENT);

  // Label Kiri
  gfx->setTextColor(TEXT_MUTED);
  gfx->setTextSize(2);
  gfx->setCursor(40, 90);  gfx->print("Berat  :");
  gfx->setCursor(40, 130); gfx->print("Tinggi :");
  gfx->setCursor(40, 170); gfx->print("BMI    :");

  // Nilai Kanan
  gfx->setTextColor(TEXT_MAIN);
  gfx->setCursor(180, 90);  gfx->print(weight, 1); gfx->print(" kg");
  gfx->setCursor(180, 130); gfx->print(height, 1); gfx->print(" cm");

  gfx->setCursor(180, 170);
  if (bmi > 0) gfx->print(bmi, 1);
  else gfx->print("Loading...");

  // Area Status (Kotak Background untuk kontras)
  gfx->fillRoundRect(40, 210, 400, 70, 10, CARD_BG);
  gfx->drawRoundRect(40, 210, 400, 70, 10, TEXT_MAIN); // Garis tepi kotak agar lebih jelas

  // Tulisan Status (Paling Utama)
  gfx->setTextColor(TEXT_MAIN); // Gunakan warna putih/utama agar paling kontras
  gfx->setTextSize(3);
  gfx->setCursor(60, 230);
  
  if (statusStunting == "Memuat...") {
      gfx->print("SEDANG ANALISA...");
  } else {
      gfx->print(statusStunting);
  }
}

void drawWifiUpdate() {
  gfx->fillScreen(BG_COLOR);
  
  gfx->fillRoundRect(30, 50, 420, 180, 10, CARD_BG);
  gfx->drawRoundRect(30, 50, 420, 180, 10, CARD_BORDER);
  
  gfx->setTextColor(COLOR_SUCCESS); gfx->setTextSize(3);
  gfx->setCursor(65, 85);  gfx->println("WI-FI TER-UPDATE");
  
  gfx->setTextColor(TEXT_MUTED); gfx->setTextSize(2);
  gfx->setCursor(65, 145); gfx->print("SSID: "); 
  gfx->setTextColor(TEXT_MAIN); gfx->println(ssid);
  
  gfx->setTextColor(COLOR_WARN); gfx->setTextSize(1);
  gfx->setCursor(155, 200); gfx->println("Melakukan Reboot Sistem...");
}

void drawFinish() {

  gfx->fillScreen(BG_COLOR);

  gfx->fillCircle(240,110,45,COLOR_SUCCESS);

  gfx->setTextColor(BG_COLOR);
  gfx->setTextSize(5);
  gfx->setCursor(220,85);
  gfx->print("V");

  gfx->setTextColor(COLOR_SUCCESS);
  gfx->setTextSize(3);

  gfx->setCursor(120,190);
  gfx->println("SCAN COMPLETED");

  gfx->setTextColor(TEXT_MUTED);
  gfx->setTextSize(2);

  gfx->setCursor(160,240);
  gfx->println("Thank You");
}

// ======================================================
// ARDUINO SETUP CORE
// ======================================================
void setup() {
  Serial.begin(115200);
  
  gfx->begin();
  drawBoot();

  preferences.begin("smale_wifi", true); 
  String stored_ssid = preferences.getString("ssid", "smale"); 
  String stored_pass = preferences.getString("password", "smale123"); 
  preferences.end();

  stored_ssid.toCharArray(ssid, 50);
  stored_pass.toCharArray(password, 50);
  
  Serial.print("Booting menggunakan SSID: "); Serial.println(ssid);

  LoadCell.begin();
  LoadCell.setReverseOutput();
  unsigned long stabilizingtime = 2000;
  bool tare = true;
  LoadCell.start(stabilizingtime, tare);
  
  if (!LoadCell.getTareTimeoutFlag()) {
    LoadCell.setCalFactor(MY_CAL_FACTOR);
  }

  pinMode(TRIG_PIN, OUTPUT);
  pinMode(ECHO_PIN, INPUT);

  WiFi.begin(ssid, password);
  client.setServer(mqtt_server, 1883);
  client.setCallback(callback);
  bootStartTime = millis();
  connectionStartTime = millis();
  screenTimer = millis();
}

// ======================================================
// STATE MACHINE LOOP MAIN RUNNER
// ======================================================
void loop() {
  wifiReconnect();
  reconnectMQTT();
  checkConnectionMode();
  client.loop();

  if (currentScreen != lastScreen) {
    needRedraw = true;
    lastScreen = currentScreen;
  }

  if (tareRequest) {
    LoadCell.tareNoDelay();
    if (LoadCell.getTareStatus()) {
      Serial.println("Zero-Tare Sukses.");
      tareRequest = false;
    }
  }

  switch (currentScreen) {
    
    case BOOT_SCREEN:
      if (millis() - screenTimer > 3000) {
        currentScreen = WIRING_SCREEN;
        screenTimer = millis();
      }
      break;

    case WIRING_SCREEN:
      if (needRedraw) {
        drawWiring();
        needRedraw = false;
      }
      
      if (!offlineMode) {
        if (mqttReady && childDataReady) {
          currentScreen = RULES_SCREEN;
          screenTimer = millis();
        }
      } else {
        if (millis() - screenTimer > 10000) {
          currentScreen = RULES_SCREEN;
          screenTimer = millis();
        }
      }
      break;

    case RULES_SCREEN:
      if (needRedraw) {
        drawRules();
        needRedraw = false;
      }
      if (millis() - screenTimer > 4000) {
        currentScreen = SCAN_SCREEN;
        screenTimer = millis();
        lastCountdown = millis();
        countdown = 5;
        
        if (!offlineMode) {
          bmi = 0.0;
          statusStunting = "Memuat...";
        }
        lastDispWeight = -1.0; lastDispHeight = -1.0; lastDispBmi = -1.0; lastDispCount = -1;
        drawScanBase(); 
      }
      break;

    case SCAN_SCREEN:
      if (millis() - lastSensorRead > 80) {
        readSensors();
        lastSensorRead = millis();
      }

      if (stableWeightCount >= REQUIRED_STABLE_READS &&
          stableHeightCount >= REQUIRED_STABLE_READS) {

          currentScreen = RESULT_SCREEN;
          screenTimer = millis();
          dataSent = false;

          stableWeightCount = 0;
          stableHeightCount = 0;
      }
      drawScanValues();
    break;   // <<< WAJIB

    case RESULT_SCREEN:
      if (needRedraw) {
        drawResult();
        needRedraw = false;
      }
      
      if (!dataSent) {
        publishResult(); 
        dataSent = true;
      }

      if (millis() - screenTimer > 8000) {
        currentScreen = FINISH_SCREEN;
        screenTimer = millis();
      }
      break;

    case WIFI_UPDATE_SCREEN:
      if (needRedraw) {
        drawWifiUpdate();
        needRedraw = false;
      }
      
      if (millis() - screenTimer > 4000) {
        Serial.println("Memulai Rebranding Jaringan... REBOOTING NOW!");
        ESP.restart(); 
      }
      break;

    case FINISH_SCREEN:
      if (needRedraw) {
        drawFinish();
        needRedraw = false;
      }
      if (millis() - screenTimer > 3000) {
        childDataReady = false;
        currentScreen = WIRING_SCREEN; 
        screenTimer = millis();
      }
      break;
  }
}