#!/bin/bash

# Скрипт для просмотра логов приложения с удаленного устройства
# Использование: ./view_logs.sh <IP_адрес> [файл|logcat]

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

PACKAGE_NAME="com.example.bitrix_app"

if [ -z "$1" ]; then
    echo -e "${RED}Ошибка: IP адрес не указан${NC}"
    echo "Использование: $0 <IP_адрес> [file|logcat|crash]"
    echo "Примеры:"
    echo "  $0 192.168.1.100 file    - Скачать файл лога"
    echo "  $0 192.168.1.100 logcat  - Показать логи в реальном времени"
    echo "  $0 192.168.1.100 crash   - Показать последний crash report"
    exit 1
fi

DEVICE_IP=$1
LOG_TYPE=${2:-logcat}

echo -e "${YELLOW}=== Просмотр логов устройства $DEVICE_IP ===${NC}"

# Подключение
echo -e "${YELLOW}Подключение к устройству...${NC}"
adb connect "$DEVICE_IP:5555"

if ! adb devices | grep -q "$DEVICE_IP:5555"; then
    echo -e "${RED}Ошибка: Не удалось подключиться к устройству${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Подключено${NC}"

case $LOG_TYPE in
    file)
        echo -e "${YELLOW}Скачивание файла логов...${NC}"
        LOG_FILE="app_log_$(date +%Y%m%d_%H%M%S).txt"
        adb shell "run-as $PACKAGE_NAME cat /data/data/$PACKAGE_NAME/files/logs/app_log_current.txt" > "$LOG_FILE"
        echo -e "${GREEN}✓ Лог сохранен в: $LOG_FILE${NC}"
        echo -e "${YELLOW}Последние 50 строк:${NC}"
        tail -50 "$LOG_FILE"
        ;;
        
    crash)
        echo -e "${YELLOW}Получение crash report...${NC}"
        CRASH_FILE="crash_report_$(date +%Y%m%d_%H%M%S).txt"
        adb shell "run-as $PACKAGE_NAME cat /data/data/$PACKAGE_NAME/files/last_crash.txt" > "$CRASH_FILE" 2>/dev/null
        
        if [ -s "$CRASH_FILE" ]; then
            echo -e "${GREEN}✓ Crash report сохранен в: $CRASH_FILE${NC}"
            cat "$CRASH_FILE"
        else
            echo -e "${GREEN}✓ Crash reports не найдены (приложение работает стабильно)${NC}"
            rm "$CRASH_FILE"
        fi
        ;;
        
    logcat|*)
        echo -e "${YELLOW}Показываются логи в реальном времени...${NC}"
        echo -e "${YELLOW}Нажмите Ctrl+C для остановки${NC}"
        echo ""
        sleep 2
        
        # Проверяем, запущено ли приложение
        PID=$(adb shell pidof -s "$PACKAGE_NAME" 2>/dev/null)
        
        if [ -n "$PID" ]; then
            echo -e "${GREEN}✓ Приложение запущено (PID: $PID)${NC}"
            # Показываем только логи этого процесса
            adb logcat --pid=$PID
        else
            echo -e "${YELLOW}⚠ Приложение не запущено${NC}"
            echo "Показываются все логи с тегом Bitrix..."
            adb logcat | grep -i bitrix
        fi
        ;;
esac
