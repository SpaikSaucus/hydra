# syntax=docker/dockerfile:1
# Reusable Android build toolchain for Hydra (JDK 17 + Android SDK 34 + Gradle 8.5).
# This image holds NO project source; mount the project at /project and run gradle
# tasks against it, caching dependencies via a mounted gradle home. Mirrors the
# toolchain used by the sibling cast-bridge project.
FROM eclipse-temurin:17-jdk

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update && apt-get install -y --no-install-recommends \
    unzip \
    wget \
    && rm -rf /var/lib/apt/lists/*

# Android SDK
ENV ANDROID_HOME=/opt/android-sdk
ENV PATH="${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${PATH}"

RUN mkdir -p ${ANDROID_HOME}/cmdline-tools && \
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O /tmp/cmdline-tools.zip && \
    unzip -q /tmp/cmdline-tools.zip -d /tmp/cmdline-tools && \
    mv /tmp/cmdline-tools/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest && \
    rm /tmp/cmdline-tools.zip

RUN yes | sdkmanager --licenses > /dev/null 2>&1 && \
    sdkmanager "platforms;android-34" "build-tools;34.0.0" "platform-tools"

# Gradle
ENV GRADLE_VERSION=8.5
ENV GRADLE_HOME=/opt/gradle
ENV PATH="${GRADLE_HOME}/bin:${PATH}"

RUN wget -q https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip -O /tmp/gradle.zip && \
    unzip -q /tmp/gradle.zip -d /opt && \
    mv /opt/gradle-${GRADLE_VERSION} ${GRADLE_HOME} && \
    rm /tmp/gradle.zip

WORKDIR /project
