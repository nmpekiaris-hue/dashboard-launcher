# Dashboard launcher (Android) — οδηγίες για Claude

Launcher αρχικής οθόνης για το κινητό του Νικόλαου (RedMagic 11 Pro). Plain Java, χωρίς AndroidX,
όλο το UI σε `app/src/main/java/gr/mpekiaris/launcher/MainActivity.java`. Απαντάμε στα ελληνικά, σύντομα.

## Ενότητες
- Ώρα/ημερομηνία· παρατεταμένο στην ημερομηνία → «Έλεγχος για ενημέρωση».
- ΣΗΜΕΡΑ: ημερολόγιο της πλατφόρμας ΜΠΕΚΙΑΡΗΣ (`https://mpekiaris.tail97f291.ts.net`, `GET /api/calendar?from=&to=`,
  `POST /api/calendar {date,title,time,shared}`, `DELETE /api/calendar/:id`, login `POST /api/login {username,password,remember}`
  με cookie συνεδρίας) + ημερολόγιο κινητού (📱).
- Γρήγορες κλήσεις, Εκκρεμότητες (τοπικά, SharedPreferences), αναζήτηση εφαρμογών, dock.

## Αυτόματη ενημέρωση — ΠΩΣ ΒΓΑΖΟΥΜΕ ΝΕΑ ΕΚΔΟΣΗ
Το κινητό διαβάζει `release/version.json` από το raw.githubusercontent.com (κάθε 3 ώρες ή χειροκίνητα),
κατεβάζει το `release/Dashboard.apk` αν `versionCode` > εγκατεστημένο και δείχνει μπάρα «Νέα έκδοση».

Σε ΚΑΘΕ αλλαγή:
1. Ανέβασε `versionCode` (+1) και `versionName` στο `app/build.gradle`.
2. `KS_PASS='<φράση>' scripts/release.sh "σημείωση αλλαγών"` — χτίζει, υπογράφει, γράφει `release/`.
3. `./gradlew testDebugUnitTest` πριν το commit.
4. Commit + push στο `main` (μαζί με `release/Dashboard.apk` και `release/version.json`).

## Κλειδί υπογραφής — ΚΡΙΣΙΜΟ
- Όλα τα APK υπογράφονται με το ΙΔΙΟ κλειδί (SHA-256 cert `2edc3f4e…b082f`). Με άλλο κλειδί το κινητό
  αρνείται την ενημέρωση και θέλει απεγκατάσταση (χάνονται κλήσεις/εκκρεμότητες/σύνδεση).
- Στο repo υπάρχει ΜΟΝΟ κρυπτογραφημένο: `keystore/dashboard.jks.enc` (AES-256-CBC, pbkdf2, 200000 iter).
  Τη φράση την έχει ο Νικόλαος — ζήτα του την. Ποτέ μη γίνει commit το `keystore/dashboard.jks`.

## Build περιβάλλον
Android SDK (platform 35, build-tools 35) με cmdline-tools στο `$ANDROID_HOME`· `local.properties` → `sdk.dir`.
Χωρίς emulator: οι δοκιμές τρέχουν με Robolectric (`app/src/test`).
