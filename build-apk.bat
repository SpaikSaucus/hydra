@echo off
setlocal enabledelayedexpansion

echo ============================================
echo  Hydra - Building release APK via Docker
echo ============================================
echo.

set "PROJECT_DIR=%~dp0"
if "!PROJECT_DIR:~-1!"=="\" set "PROJECT_DIR=!PROJECT_DIR:~0,-1!"

if not exist "!PROJECT_DIR!\.env" (
    echo ERROR: .env file not found.
    echo   copy .env.example .env   ^&^& edit the passwords
    pause
    exit /b 1
)
for /f "usebackq tokens=1,2 delims==" %%a in ("!PROJECT_DIR!\.env") do set "%%a=%%b"

if not exist "!PROJECT_DIR!\app-output" mkdir "!PROJECT_DIR!\app-output"

set "SECRETS_DIR=%TEMP%\hydra-secrets-%RANDOM%"
mkdir "!SECRETS_DIR!"
echo|set /p="!KEYSTORE_PASSWORD!" > "!SECRETS_DIR!\KEYSTORE_PASSWORD"
echo|set /p="!KEY_ALIAS!" > "!SECRETS_DIR!\KEY_ALIAS"
echo|set /p="!KEY_PASSWORD!" > "!SECRETS_DIR!\KEY_PASSWORD"

echo [1/2] Building Docker image (first time may take several minutes)...
set "DOCKER_BUILDKIT=1"
call docker build ^
    --secret id=KEYSTORE_PASSWORD,src="!SECRETS_DIR!\KEYSTORE_PASSWORD" ^
    --secret id=KEY_ALIAS,src="!SECRETS_DIR!\KEY_ALIAS" ^
    --secret id=KEY_PASSWORD,src="!SECRETS_DIR!\KEY_PASSWORD" ^
    -t hydra-builder "!PROJECT_DIR!"
set "BUILD_RESULT=!ERRORLEVEL!"

rmdir /s /q "!SECRETS_DIR!" 2>nul

if !BUILD_RESULT! NEQ 0 (
    echo ERROR: Docker build failed. Is Docker Desktop running?
    pause
    exit /b 1
)

echo.
echo [2/2] Extracting APK...
call docker run --rm -v "!PROJECT_DIR!\app-output:/output" hydra-builder

rem Persist the signing keystore (git-ignored) so future releases keep the same
rem signature and can upgrade in place. Keep .env passwords stable once it exists.
if not exist "!PROJECT_DIR!\hydra-release.keystore" (
    if exist "!PROJECT_DIR!\app-output\hydra-release.keystore" (
        copy /y "!PROJECT_DIR!\app-output\hydra-release.keystore" "!PROJECT_DIR!\hydra-release.keystore" >nul
        echo  Keystore saved to hydra-release.keystore ^(git-ignored^) - BACK IT UP.
    )
)

echo.
echo ============================================
echo  SUCCESS! app-output\Hydra.apk
echo  Install: adb install app-output\Hydra.apk
echo ============================================
pause
endlocal
