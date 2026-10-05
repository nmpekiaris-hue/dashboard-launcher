#!/usr/bin/env bash
# Χτίζει υπογεγραμμένο APK και ενημερώνει το release/ ώστε το κινητό να το βρει μόνο του.
# Χρήση: scripts/release.sh "Σύντομη σημείωση αλλαγών"
set -euo pipefail
cd "$(dirname "$0")/.."
NOTES="${1:-}"
if [ ! -f keystore/dashboard.jks ]; then
  : "${KS_PASS:?Λείπει το keystore. Ορίσε KS_PASS (φράση από τον χρήστη) για αποκρυπτογράφηση}"
  openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 -in keystore/dashboard.jks.enc -out keystore/dashboard.jks -pass "pass:$KS_PASS"
fi
./gradlew assembleRelease --no-daemon -q
APK=app/build/outputs/apk/release/app-release.apk
CODE=$(grep -oE "versionCode [0-9]+" app/build.gradle | grep -oE "[0-9]+")
NAME=$(grep -oE "versionName '[^']+'" app/build.gradle | sed "s/versionName '//;s/'//")
cp "$APK" release/Dashboard.apk
python3 - "$CODE" "$NAME" "$NOTES" <<'PY'
import json, sys
code, name, notes = int(sys.argv[1]), sys.argv[2], sys.argv[3]
json.dump({"versionCode": code, "versionName": name, "notes": notes,
           "apk": "https://raw.githubusercontent.com/nmpekiaris-hue/dashboard-launcher/main/release/Dashboard.apk"},
          open("release/version.json", "w"), ensure_ascii=False, indent=2)
PY
echo "Έτοιμο: έκδοση $NAME ($CODE)"
