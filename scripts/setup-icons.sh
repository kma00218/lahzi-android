#!/usr/bin/env bash
# Generates all required Android icon PNGs from app-icon.png (white logo)
# Requires: ImageMagick (convert command)
# Usage: bash scripts/setup-icons.sh
set -e

SRC="app-icon.png"
BG="#0B1220"

if [ ! -f "$SRC" ]; then
  echo "ERROR: $SRC not found. Place app-icon.png in the android-twa/ root."
  exit 1
fi

echo "Generating launcher icons from $SRC (white logo on navy background)..."

# ── Launcher icons (white logo centered on navy background) ───────────────
declare -A SIZES=(
  ["app/src/main/res/mipmap-mdpi"]=48
  ["app/src/main/res/mipmap-hdpi"]=72
  ["app/src/main/res/mipmap-xhdpi"]=96
  ["app/src/main/res/mipmap-xxhdpi"]=144
  ["app/src/main/res/mipmap-xxxhdpi"]=192
)

for DIR in "${!SIZES[@]}"; do
  SIZE=${SIZES[$DIR]}
  LOGO_SIZE=$((SIZE * 70 / 100))
  mkdir -p "$DIR"

  # Square icon: navy background + white logo centered at 70%
  convert -size "${SIZE}x${SIZE}" "xc:${BG}" \
    \( "$SRC" -resize "${LOGO_SIZE}x${LOGO_SIZE}" \) \
    -gravity center -composite \
    "$DIR/ic_launcher.png"

  # Round icon: navy circle + white logo centered at 70%
  convert -size "${SIZE}x${SIZE}" "xc:${BG}" \
    \( "$SRC" -resize "${LOGO_SIZE}x${LOGO_SIZE}" \) \
    -gravity center -composite \
    \( +clone -alpha extract \
       -draw "fill black polygon 0,0 0,${SIZE} ${SIZE},0 fill white circle $((SIZE/2)),$((SIZE/2)) $((SIZE/2)),0" \
       \( +clone -flip \) -compose Multiply -composite \
       \( +clone -flop \) -compose Multiply -composite \
    \) -alpha off -compose CopyOpacity -composite \
    "$DIR/ic_launcher_round.png" 2>/dev/null \
    || cp "$DIR/ic_launcher.png" "$DIR/ic_launcher_round.png"

  echo "  ✓ ${SIZE}×${SIZE} → $DIR"
done

# ── Adaptive icon foreground (API 26+) — white logo on transparent bg ────
mkdir -p "app/src/main/res/mipmap-anydpi-v26"
mkdir -p "app/src/main/res/drawable-xxxhdpi"
FGSIZE=308
convert -size "432x432" "xc:none" \
  \( "$SRC" -resize "${FGSIZE}x${FGSIZE}" \) \
  -gravity center -composite \
  "app/src/main/res/drawable-xxxhdpi/ic_launcher_foreground.png"

cat > "app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml" <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/colorSplashBackground"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
</adaptive-icon>
EOF
cp "app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml" \
   "app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml"
echo "  ✓ adaptive icon XML → mipmap-anydpi-v26"

# ── Splash screen image — white logo on navy background ───────────────────
mkdir -p "app/src/main/res/drawable-nodpi"
SPLASH_LOGO=360
convert -size "512x512" "xc:${BG}" \
  \( "$SRC" -resize "${SPLASH_LOGO}x${SPLASH_LOGO}" \) \
  -gravity center -composite \
  "app/src/main/res/drawable-nodpi/splash_icon.png"
echo "  ✓ 512×512 splash_icon.png → drawable-nodpi"

# ── Google Play Store icon (512×512) ─────────────────────────────────────
convert -size "512x512" "xc:${BG}" \
  \( "$SRC" -resize "360x360" \) \
  -gravity center -composite \
  "play-store-icon-512.png"
echo "  ✓ 512×512 → play-store-icon-512.png (Google Play listing)"

echo ""
echo "All icons generated successfully!"
