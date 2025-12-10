#!/bin/bash

echo "========================================"
echo "Compilazione e installazione Pastiera"
echo "========================================"
echo ""

# Set JAVA_HOME to Android Studio's JDK
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

# Check if JAVA_HOME is valid
if [ ! -f "$JAVA_HOME/bin/java" ]; then
    echo "ERRORE: Java non trovato in $JAVA_HOME"
    echo "Verifica che Android Studio sia installato."
    exit 1
fi

# Find ADB path
ADB_PATH=""
if [ -f "$HOME/Library/Android/sdk/platform-tools/adb" ]; then
    ADB_PATH="$HOME/Library/Android/sdk/platform-tools/adb"
elif [ -f "./local.properties" ]; then
    SDK_DIR=$(grep "sdk.dir" ./local.properties | cut -d'=' -f2)
    if [ -f "$SDK_DIR/platform-tools/adb" ]; then
        ADB_PATH="$SDK_DIR/platform-tools/adb"
    fi
fi

if [ -z "$ADB_PATH" ]; then
    ADB_PATH=$(which adb 2>/dev/null)
fi

if [ -z "$ADB_PATH" ] || [ ! -f "$ADB_PATH" ]; then
    echo "ERRORE: ADB non trovato."
    echo "Verifica che Android SDK sia installato e che platform-tools sia presente."
    exit 1
fi

# Check if device is connected
echo "Verifica dispositivo connesso..."
DEVICE_COUNT=$("$ADB_PATH" devices | grep -v "List" | grep "device$" | wc -l | tr -d ' ')
if [ "$DEVICE_COUNT" -eq 0 ]; then
    echo "ATTENZIONE: Nessun dispositivo Android connesso."
    echo "Collega un dispositivo o avvia un emulatore prima di continuare."
    echo ""
    echo "Compilazione dell'APK..."
    ./gradlew assembleDebug
    if [ $? -eq 0 ]; then
        echo ""
        echo "========================================"
        echo "APK compilato con successo!"
        echo "========================================"
        echo "APK disponibile in: app/build/outputs/apk/debug/app-debug.apk"
        exit 0
    else
        echo ""
        echo "ERRORE: La compilazione non è riuscita."
        exit 1
    fi
fi

# Compile and install the app
./gradlew installDebug

# Check if installation was successful
if [ $? -eq 0 ]; then
    echo ""
    echo "========================================"
    echo "Installazione completata con successo!"
    echo "========================================"
    echo ""
    echo "Avvio dell'app sul dispositivo..."
    
    # Launch the app on Android device
    "$ADB_PATH" shell am start -n it.palsoftware.pastiera/.MainActivity
    
    if [ $? -eq 0 ]; then
        echo ""
        echo "App avviata con successo!"
        exit 0
    else
        echo ""
        echo "ERRORE: Impossibile avviare l'app."
        echo "Verifica che il dispositivo sia connesso e che ADB sia configurato correttamente."
        exit 1
    fi
else
    echo ""
    echo "ERRORE: La compilazione/installazione non è riuscita."
    echo "Verifica gli errori sopra."
    exit 1
fi

