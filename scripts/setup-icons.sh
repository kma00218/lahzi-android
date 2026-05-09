#!/usr/bin/env bash
# Generates all required Android icon PNGs from app-icon.png
# Requires: ImageMagick (convert command)
# Usage: bash scripts/setup-icons.sh
set -e

SRC="app-icon.png"

if [ ! -f "$SRC" ]; then
  echo "ERROR: $SRC not found. Place app-icon.png in the android-twa/ root."
  exit 1
fi

echo "Generating launcher icons from $SRC ..."

# ── Launcher icons (square) ────────────────────────────────────────────────
declare -A SIZES=(
  ["app/src/main/res/mipmap-mdpi"]=48
  ["app/src/main/res/mipmap-hdpi"]=72
  ["app/src/main/res/mipmap-xhdpi"]=96
  ["app/src/main/res/mipmap-xxhdpi"]=144
  ["app/src/main/res/mipmap-xxxhdpi"]=192
)

for DIR in "${!SIZES[@]}"; do
  SIZE=${SIZES[$DIR]}
  mkdir -p "$DIR"
  convert "$SRC" -resize "${SIZE}x${SIZE}" "$DIR/ic_launcher.png"
  # Round icon: circle-cropped
  convert "$SRC" -resize "${SIZE}x${SIZE}" \
    \( +clone -alpha extract \
       -draw "fill black polygon 0,0 0,${SIZE} ${SIZE},0 fill white circle $((SIZE/2)),$((SIZE/2)) $((SIZE/2)),0" \
       \( +clone -flip \) -compose Multiply -composite \
       \( +clone -flop \) -compose Multiply -composite \
    \) -alpha off -compose CopyOpacity -composite \
    "$DIR/ic_launcher_round.png" 2>/dev/null \
    || cp "$DIR/ic_launcher.png" "$DIR/ic_launcher_round.png"
  echo "  ✓ ${SIZE}×${SIZE} → $DIR"
done

# ── Adaptive icon foreground (API 26+) ────────────────────────────────────
mkdir -p "app/src/main/res/mipmap-anydpi-v26"
mkdir -p "app/src/main/res/drawable-xxxhdpi"
convert "$SRC" -resize "432x432" \
  -background "#0B1220" -gravity center -extent "432x432" \
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

# ── Splash screen image ────────────────────────────────────────────────────
mkdir -p "app/src/main/res/drawable-nodpi"
convert "$SRC" -resize "512x512" \
  "app/src/main/res/drawable-nodpi/splash_icon.png"
echo "  ✓ 512×512 splash_icon.png → drawable-nodpi"

# ── Google Play Store icon (512×512) ──────────────────────────────────────
convert "$SRC" -resize "512x512" "play-store-icon-512.png"
echo "  ✓ 512×512 → play-store-icon-512.png (Google Play listing)"

echo ""
echo "All icons generated successfully!"
