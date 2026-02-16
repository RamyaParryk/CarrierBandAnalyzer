# Band Analyzer for Android 🇯🇵

[🇯🇵 **Japanese**](./README_JP.md) | [💻 **View Source Code on GitHub**](https://github.com/RamyaParryk/CarrierBandAnalyzer)

---
(以下そのまま)

---

**Band Analyzer** is a specialized Android tool that visualizes and analyzes mobile network frequency bands. It is optimized for the complex network environment of Japanese carriers (Docomo, au, SoftBank, Rakuten Mobile) and their sub-brands (ahamo, povo, LINEMO, etc.).

## 📱 Features

* **Real-time Visualization:** Instantly displays the currently connected LTE/5G bands (e.g., Band 1, Band 19, n78).
* **Carrier Optimization:** Automatically detects major Japanese carriers and MVNOs to provide accurate band information.
* **Platinum Band Detection:** Highlights "Platinum Bands" (B8, B18, B19, B28) which are essential for stable connectivity in rural areas or indoors.
* **Background Monitoring:** Uses a foreground service to record band history and coverage even when the app is closed or the screen is off.

## 🛠 Tech Stack

* **Language:** Kotlin
* **UI Framework:** Jetpack Compose (Material Design 3)
* **Architecture:** MVVM
* **Android APIs:** TelephonyManager, Foreground Service

## 📡 Supported Bands (Japan)

This app supports the detection of all major bands used in Japan:

| Category | Bands | Description |
| :--- | :--- | :--- |
| **Platinum (Low)** | B8, B18, B19, B26, B28 | Wide coverage (Rural/Indoor) |
| **Main (Mid)** | B1, B3, B11, B21 | High capacity & Standard speed |
| **High Speed (4G)** | B41, B42 | Ultra high-speed LTE |
| **5G (Sub6)** | n77, n78, n79 | Standard 5G coverage |
| **5G (mmWave)** | n257 | Ultra high-speed 5G (Limited area) |

## 📝 License

This project is licensed under the MIT License.