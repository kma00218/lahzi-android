#!/usr/bin/env bash
# Generates a release keystore for signing the Lahzi Android app.
# Run this ONCE and keep the .jks file safe — you need it for every update.
#
# Usage:
#   bash generate-keystore.sh
#
# You will be prompted for passwords. Use strong, memorable passwords.
# NEVER commit the .jks file or passwords to version control.
set -e

mkdir -p keystore

echo "================================================================"
echo "  LAHZI Android Keystore Generator"
echo "  Developer: Alphoton Digital"
echo "================================================================"
echo ""
echo "You will be asked to set two passwords:"
echo "  1. Keystore password  (store password)"
echo "  2. Key password       (can be the same as keystore password)"
echo ""
echo "IMPORTANT: Save these passwords somewhere safe."
echo "  If you lose them you CANNOT update the app on Google Play."
echo ""

read -sp "Enter KEYSTORE password: " STORE_PASS
echo ""
read -sp "Confirm KEYSTORE password: " STORE_PASS2
echo ""

if [ "$STORE_PASS" != "$STORE_PASS2" ]; then
  echo "Passwords do not match. Aborting."
  exit 1
fi

read -sp "Enter KEY password (or press Enter to use same password): " KEY_PASS
echo ""

if [ -z "$KEY_PASS" ]; then
  KEY_PASS="$STORE_PASS"
fi

KEYSTORE_FILE="keystore/lahzi-release.jks"

keytool -genkey -v \
  -keystore "$KEYSTORE_FILE" \
  -alias "lahzi" \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -dname "CN=Alphoton Digital, OU=Mobile, O=Alphoton Digital, L=Tripoli, ST=Tripoli, C=LY" \
  -storepass "$STORE_PASS" \
  -keypass "$KEY_PASS"

echo ""
echo "================================================================"
echo "  Keystore created: $KEYSTORE_FILE"
echo "================================================================"
echo ""
echo "Your SHA-256 fingerprint (paste into assetlinks.json):"
keytool -list -v \
  -keystore "$KEYSTORE_FILE" \
  -alias "lahzi" \
  -storepass "$STORE_PASS" 2>/dev/null | grep "SHA256:"

echo ""
echo "================================================================"
echo "  NEXT STEPS"
echo "================================================================"
echo "1. Copy the SHA-256 fingerprint above"
echo "2. Open  artifacts/bassa-today/public/.well-known/assetlinks.json"
echo "3. Replace REPLACE_WITH_YOUR_SHA256_FINGERPRINT with the value"
echo "4. Deploy the website so the file is live at:"
echo "   https://lahzi.ly/.well-known/assetlinks.json"
echo ""
echo "For GitHub Actions, add these secrets to your repo:"
echo "  KEYSTORE_BASE64  ->  \$(base64 -w 0 $KEYSTORE_FILE)"
echo "  KEYSTORE_PASSWORD -> your store password"
echo "  KEY_ALIAS        -> lahzi"
echo "  KEY_PASSWORD     -> your key password"
echo ""
echo "Base64 of your keystore (copy this as KEYSTORE_BASE64 secret):"
base64 -w 0 "$KEYSTORE_FILE"
echo ""
