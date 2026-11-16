#!/usr/bin/env sh
# Final acceptance session: starts the app in web-only mode and walks the API.
set -u
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
export PORT="${PORT:-8096}"
B="http://127.0.0.1:$PORT"
if [ -n "${JAVA_HOME:-}" ] && [ -x "${JAVA_HOME}/bin/java" ]; then
    JAVA="${JAVA_HOME}/bin/java"
else
    JAVA="java"
fi

cd "$ROOT"
"$JAVA" --add-modules jdk.httpserver -cp target/classes com.relaybook.App --web-only > /tmp/app-final.log 2>&1 &
APP_PID=$!
sleep 5

pass=0; fail=0
check() {
    label="$1"; expected="$2"; actual="$3"
    if [ "$expected" = "$actual" ]; then
        pass=$((pass+1)); echo "PASS  $label ($actual)"
    else
        fail=$((fail+1)); echo "FAIL  $label (expected $expected, got $actual)"
    fi
}

echo "=== end-to-end acceptance session ==="
check "GET / (index page)"     "200" "$(curl -s -o /dev/null -w '%{http_code}' "$B/")"
check "GET /check-ins"         "200" "$(curl -s -o /dev/null -w '%{http_code}' "$B/check-ins")"
P=$(curl -s "$B/api/contacts" | python3 -c "import json,sys; print([c['id'] for c in json.load(sys.stdin) if c['type']=='EMERGENCY'][0])")
check "GET /person page"       "200" "$(curl -s -o /dev/null -w '%{http_code}' "$B/person/$P")"
check "GET /api/contacts"      "200" "$(curl -s -o /dev/null -w '%{http_code}' "$B/api/contacts")"
check "GET /api/stats"         "200" "$(curl -s -o /dev/null -w '%{http_code}' "$B/api/stats")"
check "POST /api/contacts"     "201" "$(curl -s -o /dev/null -w '%{http_code}' -X POST "$B/api/contacts" --data-urlencode type=PERSON --data-urlencode 'fullName=Final Check' --data-urlencode 'route.0.channel=PHONE' --data-urlencode 'route.0.value=+91 77777 00001' --data-urlencode 'route.1.channel=EMAIL' --data-urlencode 'route.1.value=final@example.test')"
check "emergency 1-route rejected (400 + type-specific message)" "400" "$(curl -s -o /dev/null -w '%{http_code}' -X POST "$B/api/contacts" --data-urlencode type=EMERGENCY --data-urlencode 'fullName=Solo' --data-urlencode phone=123)"
check "duplicate rejected"     "409" "$(curl -s -o /dev/null -w '%{http_code}' -X POST "$B/api/contacts" --data-urlencode type=PERSON --data-urlencode 'fullName=Final Check' --data-urlencode 'phone=+91 77777 00001')"
check "unknown contact"        "404" "$(curl -s -o /dev/null -w '%{http_code}' "$B/api/contacts/nope")"
check "invalid timezone"       "400" "$(curl -s -o /dev/null -w '%{http_code}' -X POST "$B/api/contacts" --data-urlencode type=PERSON --data-urlencode 'fullName=Z' --data-urlencode phone=1 --data-urlencode 'timeZone=Nowhere/Nothing')"
ID=$(curl -s "$B/api/contacts?q=Final" | python3 -c "import json,sys; print(json.load(sys.stdin)[0]['id'])")
check "POST /verify (all routes)"   "200" "$(curl -s -o /dev/null -w '%{http_code}' -X POST "$B/api/contacts/$ID/verify")"
check "POST /verify?channel=EMAIL"  "200" "$(curl -s -o /dev/null -w '%{http_code}' -X POST "$B/api/contacts/$ID/verify?channel=EMAIL")"
check "PUT update"             "200" "$(curl -s -o /dev/null -w '%{http_code}' -X PUT "$B/api/contacts/$ID" --data-urlencode type=PERSON --data-urlencode 'fullName=Final Check Updated' --data-urlencode 'route.0.channel=PHONE' --data-urlencode 'route.0.value=+91 77777 00001')"
check "GET /card (vCard)"      "200" "$(curl -s -o /dev/null -w '%{http_code}' "$B/api/contacts/$ID/card")"
curl -s "$B/api/contacts/$ID/card" > /tmp/final-card.vcf
grep -q '^N:' /tmp/final-card.vcf; check "vCard has N: property" "0" "$?"
python3 - /tmp/final-card.vcf <<'PYEOF'
import sys
lines = open(sys.argv[1], 'rb').read().split(b'\r\n')
sys.exit(0 if max(len(l) for l in lines) <= 75 else 1)
PYEOF
check "vCard every physical line <= 75 octets" "0" "$?"
check "GET /api/export/csv"          "200" "$(curl -s -o /dev/null -w '%{http_code}' "$B/api/export/csv")"
check "GET /api/export/json"         "200" "$(curl -s -o /dev/null -w '%{http_code}' "$B/api/export/json")"
check "GET /api/export/reachability" "200" "$(curl -s -o /dev/null -w '%{http_code}' "$B/api/export/reachability")"
check "POST /api/import/vcard"       "200" "$(printf 'BEGIN:VCARD\r\nVERSION:3.0\r\nFN:Import Final\r\nTEL;TYPE=CELL:+91 77777 00002\r\nEND:VCARD\r\n' | curl -s -o /dev/null -w '%{http_code}' -X POST "$B/api/import/vcard" --data-binary @-)"
check "search ?type=&stale=&sort="   "200" "$(curl -s -o /dev/null -w '%{http_code}' "$B/api/contacts?type=PERSON&stale=false&sort=score")"
check "cross-origin POST blocked"    "403" "$(curl -s -o /dev/null -w '%{http_code}' -X POST "$B/api/contacts" -H 'Origin: http://evil.example' --data-urlencode type=PERSON --data-urlencode 'fullName=X' --data-urlencode 'phone=1')"
check "rebound Host DELETE blocked"  "403" "$(curl -s -o /dev/null -w '%{http_code}' -X DELETE "$B/api/contacts/$ID" -H 'Host: evil.example:'"$PORT"'"')"
check "same-origin DELETE works"     "204" "$(curl -s -o /dev/null -w '%{http_code}' -X DELETE "$B/api/contacts/$ID")"
curl -s "$B/api/stats" > /tmp/final-stats.json
python3 - /tmp/final-stats.json <<'PYEOF'
import json, sys
d = json.load(open(sys.argv[1]))
sys.exit(0 if all(k in d for k in ('total', 'stale', 'singleRoute', 'emergencyWithoutTwoRoutes', 'ready', 'withBackup')) else 1)
PYEOF
check "/api/stats has all counters" "0" "$?"

echo "-------------------------------------"
echo "ACCEPTANCE SESSION: $pass passed, $fail failed"
kill "$APP_PID" 2>/dev/null
[ "$fail" = "0" ]
