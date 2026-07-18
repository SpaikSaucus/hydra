#!/bin/bash
echo "============================================"
echo " Hydra - Building release APK via Docker"
echo "============================================"
echo ""

if [ ! -f ".env" ]; then
    echo "ERROR: .env file not found."
    echo "  cp .env.example .env   # then edit the passwords"
    exit 1
fi
source .env

mkdir -p app-output

SECRETS_DIR=$(mktemp -d)
echo -n "$KEYSTORE_PASSWORD" > "$SECRETS_DIR/KEYSTORE_PASSWORD"
echo -n "$KEY_ALIAS" > "$SECRETS_DIR/KEY_ALIAS"
echo -n "$KEY_PASSWORD" > "$SECRETS_DIR/KEY_PASSWORD"

echo "[1/2] Building Docker image (first time: 10-20 min, downloads SDK/JDK)..."
DOCKER_BUILDKIT=1 docker build \
    --secret id=KEYSTORE_PASSWORD,src="$SECRETS_DIR/KEYSTORE_PASSWORD" \
    --secret id=KEY_ALIAS,src="$SECRETS_DIR/KEY_ALIAS" \
    --secret id=KEY_PASSWORD,src="$SECRETS_DIR/KEY_PASSWORD" \
    -t hydra-builder .
BUILD_RESULT=$?

rm -rf "$SECRETS_DIR"

if [ $BUILD_RESULT -ne 0 ]; then
    echo ""
    echo "ERROR: Docker build failed. Make sure Docker is running."
    exit 1
fi

echo ""
echo "[2/2] Extracting APK..."
docker run --rm -v "$(pwd)/app-output:/output" hydra-builder

# Persist the signing keystore (git-ignored) so future releases keep the same
# signature and can upgrade in place. Keep .env safe: its passwords must stay
# stable once this file exists.
if [ ! -f "hydra-release.keystore" ] && [ -f "app-output/hydra-release.keystore" ]; then
    cp app-output/hydra-release.keystore ./hydra-release.keystore
    echo " Keystore saved to ./hydra-release.keystore (git-ignored) - BACK IT UP."
fi

echo ""
echo "============================================"
echo " SUCCESS! app-output/Hydra.apk"
echo " Install: adb install app-output/Hydra.apk"
echo "============================================"
