# syntax=docker/dockerfile:1
# Self-contained release build: produces a signed release APK.
# Mirrors the sibling cast-bridge build approach.
FROM eclipse-temurin:17-jdk

ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends unzip wget && rm -rf /var/lib/apt/lists/*

ENV ANDROID_HOME=/opt/android-sdk
ENV PATH="${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${PATH}"
RUN mkdir -p ${ANDROID_HOME}/cmdline-tools && \
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O /tmp/clt.zip && \
    unzip -q /tmp/clt.zip -d /tmp/clt && \
    mv /tmp/clt/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest && rm /tmp/clt.zip
RUN yes | sdkmanager --licenses > /dev/null 2>&1 && \
    sdkmanager "platforms;android-34" "build-tools;34.0.0" "platform-tools"

ENV GRADLE_VERSION=8.5
ENV GRADLE_HOME=/opt/gradle
ENV PATH="${GRADLE_HOME}/bin:${PATH}"
RUN wget -q https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip -O /tmp/g.zip && \
    unzip -q /tmp/g.zip -d /opt && mv /opt/gradle-${GRADLE_VERSION} ${GRADLE_HOME} && rm /tmp/g.zip

WORKDIR /project
COPY . .

# Sign with the keystore committed... never: hydra-release.keystore is git-ignored
# but COPY'd when present in the project root, so successive releases keep the SAME
# signature (required for in-place upgrades). Only when absent is a fresh keystore
# generated from the BuildKit secrets (never persisted in image layers).
RUN --mount=type=secret,id=KEYSTORE_PASSWORD \
    --mount=type=secret,id=KEY_ALIAS \
    --mount=type=secret,id=KEY_PASSWORD \
    export KEYSTORE_PASSWORD=$(cat /run/secrets/KEYSTORE_PASSWORD) && \
    export KEY_ALIAS=$(cat /run/secrets/KEY_ALIAS) && \
    export KEY_PASSWORD=$(cat /run/secrets/KEY_PASSWORD) && \
    export KEYSTORE_PATH=/project/hydra-release.keystore && \
    if [ ! -f "$KEYSTORE_PATH" ]; then \
        keytool -genkeypair -v -keystore $KEYSTORE_PATH \
            -alias ${KEY_ALIAS} -keyalg RSA -keysize 2048 -validity 10000 \
            -storepass ${KEYSTORE_PASSWORD} -keypass ${KEY_PASSWORD} \
            -dname "CN=Hydra, O=Hydra, L=Unknown, ST=Unknown, C=AR" 2>/dev/null; \
    fi && \
    gradle assembleRelease --no-daemon

# Export the keystore alongside the APK so build scripts can persist it in the
# project root (git-ignored) and future builds reuse the same signature.
CMD ["/bin/sh", "-c", "cp /project/app/build/outputs/apk/release/app-release.apk /output/Hydra.apk && cp /project/hydra-release.keystore /output/hydra-release.keystore && echo 'APK + keystore copied to /output'"]
