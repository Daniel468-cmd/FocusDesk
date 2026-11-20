# 📱 FocusDesk — Smart Office & Study Companion

**FocusDesk** este o aplicație mobilă Android concepută pentru optimizarea sesiunilor de lucru și studiu prin monitorizarea stării dispozitivului utilizând senzori hardware nativi (Proximitate și Lumină ambientală). Aplicația oferă un sistem inteligent de pauză la întrerupere, automatizează modul *Do Not Disturb* (DND) și păstrează un jurnal detaliat al activității în cel de-al doilea ecran dedicat statisticilor.

Proiect realizat pentru disciplina **Sisteme de Calcul Mobil / Sistem de Aplicații Mobile (SMA)**.

---

## ✨ Funcționalități Principale

- **⏱️ Mod Focus Face-Down (Senzor Proximitate):**
  - Sesizarea automată a așezării telefonului cu ecranul în jos pe birou.
  - Pornirea și gestionarea numărătorii inverse configurabile (implicit `00:10`, ajustabilă în minute și secunde).
  - Activarea automată a modului **Do Not Disturb** pe durata sesiunii active.

- **⚠️ Pauză Inteligentă & Avertisment la Ridicare Prematură:**
  - Dacă utilizatorul ridică telefonul înainte ca timpul să se scurgă, cronometrul **îngheață la secunda exactă**.
  - Dispozitivul emite un **pattern dublu de vibrație** și o **notificare de avertisment** (*Warning*).
  - Sesiunea se reluiază exact de unde a rămas imediat ce telefonul este așezat din nou cu fața în jos.

- **💡 Asistent Lumină Ambientală (Senzor Luxmetru):**
  - Monitorizarea continuă a intensității luminoase în spațiul de lucru (prag de `50 lux`).
  - Alertă tactilă și notificare de sistem în caz de lumină insuficientă pentru protejarea sănătății ochilor.

- **🔔 Notificări & Alarme Personalizate:**
  - Selector nativ de tonuri (*Ringtone Picker*) pentru sunetul de finalizare.
  - Buton dedicat de oprire rapidă a alarmei la finalul sesiunii.

- **📊 Ecranul 2: Statistici & Istoric Activitate (`StatsActivity`):**
  - **3 Indicatori Agregați:** Sesiuni finalizate, Timp total de focus acumulat și Numărul de ridicări/întreruperi.
  - **Jurnal de Activitate:** Lista cronologică a tuturor sesiunilor înregistrate (`✅ Finalizată` sau `⚠️ Întreruptă`), marcate cu ora exactă și durata efectivă.
  - Opțiune de resetare completă a istoricului.

---

## 🏗️ Arhitectură și Componente Software

Aplicația este structurată pe **2 ecrane (Activities)** și utilizează un repozitoriu singleton de date partajate în memorie:

| Componentă | Tip | Descriere |
| :--- | :--- | :--- |
| **`MainActivity`** | `AppCompatActivity` | **Ecranul 1 (Principal):** Gestionează senzorii hardware, cronometrul cu pauză, alertele sonore/tactile și starea DND. |
| **`StatsActivity`** | `AppCompatActivity` | **Ecranul 2 (Statistici):** Afișează indicatorii agregați și jurnalul cronologic al sesiunilor. |
| **`SessionRepository`** | `Kotlin Singleton (object)` | Gestionează stocarea temporară a meciurilor/sesiunilor și calculul statisticilor partajate între cele două ecrane. |
| **`activity_main.xml`** | Layout | Interfața ecranului principal cu carduri pentru cronometru, asistent de lumină și navigare. |
| **`activity_stats.xml`** | Layout | Interfața ecranului secundar cu 3 carduri de metrici și lista derulabilă de istoric. |

---

## 🛠️ Senzori Hardware & Servicii Sistem Utilizate

1. **`Sensor.TYPE_PROXIMITY`:**
   - Detectează prezența suprafeței biroului atunci când telefonul este întors cu ecranul în jos.
2. **`Sensor.TYPE_LIGHT`:**
   - Măsoară nivelul de iluminare ambientală în `lux`.
3. **`NotificationManager` & `NotificationChannel`:**
   - Gestionează notificările de tip `IMPORTANCE_HIGH` și comutarea filtrului de întreruperi (`INTERRUPTION_FILTER_PRIORITY`).
4. **`Vibrator` / `VibratorManager`:**
   - Feedback haptic diferențiat (vibrație simplă pentru finalizare/lumină slabă, vibrație dublă scurtă pentru avertisment ridicare).
5. **`RingtoneManager`:**
   - Permite selectarea și redarea tonurilor de sistem preferate de utilizator.

---

## 🔐 Permisiuni Android Declarate

```xml
<!-- Permisiune pentru gestionarea modului Do Not Disturb -->
<uses-permission android:name="android.permission.ACCESS_NOTIFICATION_POLICY" />

<!-- Permisiune pentru vibrații haptice -->
<uses-permission android:name="android.permission.VIBRATE" />

<!-- Permisiune pentru trimiterea notificărilor pe Android 13+ (API 33+) -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

---

## 🚀 Instalați și Rulați Proiectul

### Cerințe de Sistem:
- **Android Studio:** Ladybug / Jellyfish (sau orice versiune recentă cu Android Gradle Plugin 8.0+).
- **Min SDK:** `24` (Android 7.0 Nougat).
- **Target SDK:** `34` / `35` (Android 14/15).
- **Limbaj:** Kotlin (`JDK 17`).

### Pași pentru compilare:
1. Clonează repository-ul:
   ```bash
   git clone https://github.com/UTILIZATORUL_TAU/FocusDesk.git
   ```
2. Deschide proiectul în **Android Studio**.
3. Așteaptă sincronizarea Gradle (**Gradle Sync**).
4. Conectează un dispozitiv fizic Android (cu Debugging USB activat) sau pornește un emulator virtual.
5. Apasă pe butonul **Run (`▶`)** sau generează fișierul APK din **Build -> Build Bundle(s) / APK(s) -> Build APK(s)**.

---

## 📸 Ghid de Testare în Emulator / Telefon Fizic

1. **Testare Face-Down & Pauză:**
   - Schimbă glisorul **Proximity** în emulator de la `5.0` la `0.0` $\rightarrow$ Cronometrul pornește și DND se activează.
   - Re-schimbă glisorul la `5.0` înainte de expirare $\rightarrow$ Cronometrul îngheață, telefonul emite avertismentul vizual și notificare.
   - Pune glisorul înapoi la `0.0` $\rightarrow$ Numărătoarea reia de la secunda la care a rămas.
2. **Testare Ecran 2 (Statistici):**
   - Apasă butonul **„📊 Statistici & Istoric”** de pe ecranul principal pentru a deschide al doilea ecran și a examina jurnalul sesiunilor.