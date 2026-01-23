#!/bin/bash

# Скрипт для локальной сборки приложения
# Использование: ./build_local.sh [debug|release]

set -e

# Цвета
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

BUILD_TYPE=${1:-debug}

echo -e "${YELLOW}=== Локальная сборка APK ===${NC}"
echo "Тип сборки: $BUILD_TYPE"

# Очистка предыдущих сборок
echo -e "${YELLOW}1. Очистка...${NC}"
./gradlew clean

# Сборка
if [ "$BUILD_TYPE" = "release" ]; then
    echo -e "${YELLOW}2. Сборка Release APK...${NC}"
    ./gradlew assembleRelease
    
    APK_PATH="app/build/outputs/apk/release/app-release.apk"
    
    if [ -f "$APK_PATH" ]; then
        echo -e "${GREEN}✓ Сборка успешна!${NC}"
        echo "APK: $APK_PATH"
        ls -lh "$APK_PATH"
    else
        echo -e "${RED}✗ Ошибка: APK не найден${NC}"
        exit 1
    fi
else
    echo -e "${YELLOW}2. Сборка Debug APK...${NC}"
    ./gradlew assembleDebug
    
    APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
    
    if [ -f "$APK_PATH" ]; then
        echo -e "${GREEN}✓ Сборка успешна!${NC}"
        echo "APK: $APK_PATH"
        ls -lh "$APK_PATH"
    else
        echo -e "${RED}✗ Ошибка: APK не найден${NC}"
        exit 1
    fi
fi

echo ""
echo -e "${GREEN}=== Готово ===${NC}"
echo "Для установки на устройство:"
echo "  adb install -r $APK_PATH"
