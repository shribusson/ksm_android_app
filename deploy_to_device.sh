#!/bin/bash

# Скрипт для автоматического обновления приложения на планшете
# Использование: ./deploy_to_device.sh <IP_адрес_планшета>

set -e

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

PACKAGE_NAME="com.example.bitrix_app"
GITHUB_REPO="shribusson/ksm_android_app"

# Проверка аргументов
if [ -z "$1" ]; then
    echo -e "${RED}Ошибка: IP адрес не указан${NC}"
    echo "Использование: $0 <IP_адрес_планшета>"
    echo "Пример: $0 192.168.1.100"
    exit 1
fi

DEVICE_IP=$1

echo -e "${YELLOW}=== Обновление приложения на устройстве $DEVICE_IP ===${NC}"

# Подключение к устройству
echo -e "${YELLOW}1. Подключение к устройству через ADB...${NC}"
adb connect $DEVICE_IP:5555

# Проверка подключения
if ! adb devices | grep -q "$DEVICE_IP:5555"; then
    echo -e "${RED}Ошибка: Не удалось подключиться к устройству${NC}"
    echo "Убедитесь, что:"
    echo "  - Устройство включено"
    echo "  - ADB через WiFi настроен (adb tcpip 5555)"
    echo "  - IP адрес правильный"
    exit 1
fi

echo -e "${GREEN}✓ Подключено${NC}"

# Скачивание последней версии
echo -e "${YELLOW}2. Скачивание последней версии из GitHub...${NC}"
APK_FILE="app-release.apk"

# Удаление старого APK если есть
rm -f $APK_FILE

# Скачивание
if ! wget -q https://github.com/$GITHUB_REPO/releases/latest/download/app-release.apk -O $APK_FILE; then
    echo -e "${RED}Ошибка: Не удалось скачать APK${NC}"
    echo "Возможно, релиз еще не создан. Проверьте:"
    echo "  https://github.com/$GITHUB_REPO/releases"
    exit 1
fi

echo -e "${GREEN}✓ APK скачан${NC}"

# Установка
echo -e "${YELLOW}3. Установка APK...${NC}"
if ! adb install -r $APK_FILE; then
    echo -e "${RED}Ошибка: Не удалось установить APK${NC}"
    exit 1
fi

echo -e "${GREEN}✓ APK установлен${NC}"

# Перезапуск приложения
echo -e "${YELLOW}4. Перезапуск приложения...${NC}"
adb shell am force-stop $PACKAGE_NAME
sleep 2
adb shell am start -n $PACKAGE_NAME/.MainActivity

echo -e "${GREEN}✓ Приложение перезапущено${NC}"

# Вывод версии
echo -e "${YELLOW}5. Информация о приложении:${NC}"
adb shell dumpsys package $PACKAGE_NAME | grep -E "versionCode|versionName" | head -2

# Опциональный вывод логов
echo -e "${YELLOW}6. Последние логи приложения:${NC}"
echo "Нажмите Ctrl+C для остановки..."
sleep 2
adb logcat | grep Bitrix
