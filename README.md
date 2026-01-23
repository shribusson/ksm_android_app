# KSM Android App

[![Android CI/CD](https://github.com/shribusson/ksm_android_app/actions/workflows/android-build.yml/badge.svg)](https://github.com/shribusson/ksm_android_app/actions/workflows/android-build.yml)

Android приложение для управления задачами Bitrix24 в режиме киоска.

## 📚 Документация

- **[QUICKSTART.md](QUICKSTART.md)** - 🚀 Быстрый старт для CI/CD (начните здесь!)
- **[DEPLOYMENT.md](DEPLOYMENT.md)** - 📖 Подробное руководство по развертыванию
- **[KIOSK_SETUP.md](KIOSK_SETUP.md)** - 🔒 Настройка планшета в режиме киоска

## 🚀 CI/CD и Развертывание

### Автоматическая Сборка

При каждом push в ветку `main` или `master` автоматически запускается сборка:
- ✅ Собирается Debug APK
- ✅ APK доступен для скачивания в разделе Actions → Artifacts
- ✅ Срок хранения: 30 дней

### Релизы

Для создания релиза:

1. **Создайте тег:**
   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```

2. **GitHub Actions автоматически:**
   - Соберет Release APK
   - Создаст GitHub Release
   - Прикрепит APK к релизу
   - Добавит инструкции по установке

3. **Скачайте APK:**
   - Перейдите в [Releases](https://github.com/shribusson/ksm_android_app/releases)
   - Скачайте последний `app-release.apk`

### 📱 Удаленная Установка APK

#### Вариант 1: Через ADB (Рекомендуется)

```bash
# Скачать последний релиз
wget https://github.com/shribusson/ksm_android_app/releases/latest/download/app-release.apk

# Подключиться к устройству через WiFi (если уже настроено)
adb connect <IP_АДРЕС_ПЛАНШЕТА>:5555

# Установить APK
adb install -r app-release.apk

# Перезапустить приложение
adb shell am force-stop com.example.bitrix_app
adb shell am start -n com.example.bitrix_app/.MainActivity
```

#### Вариант 2: Через GitHub Actions Artifacts

1. Перейдите в [Actions](https://github.com/shribusson/ksm_android_app/actions)
2. Выберите последний успешный workflow
3. Скачайте `app-debug` из раздела Artifacts
4. Установите через adb

#### Вариант 3: Прямое скачивание на устройстве

1. Откройте браузер на планшете
2. Перейдите на https://github.com/shribusson/ksm_android_app/releases/latest
3. Скачайте APK
4. Разрешите установку из неизвестных источников
5. Установите APK

### 🔧 Настройка ADB через WiFi

**Первоначальная настройка (требует USB подключение один раз):**

```bash
# 1. Подключите планшет через USB
adb devices

# 2. Включите ADB через TCP/IP на порту 5555
adb tcpip 5555

# 3. Узнайте IP адрес планшета
adb shell ip addr show wlan0 | grep inet

# 4. Отключите USB и подключитесь через WiFi
adb connect <IP_АДРЕС>:5555

# 5. Проверьте подключение
adb devices
```

**После настройки подключение сохраняется. Для повторного подключения:**

```bash
adb connect <IP_АДРЕС>:5555
```

### 📊 Удаленное Чтение Логов

#### Получить логи приложения:

```bash
# Подключиться к устройству
adb connect <IP_АДРЕС>:5555

# Читать логи в реальном времени
adb logcat | grep Bitrix

# Или все логи приложения
adb logcat --pid=$(adb shell pidof -s com.example.bitrix_app)

# Сохранить логи в файл
adb logcat -d > logs.txt
```

#### Получить файл логов с устройства:

```bash
# Приложение сохраняет логи в /data/data/com.example.bitrix_app/files/logs/
adb shell "run-as com.example.bitrix_app cat /data/data/com.example.bitrix_app/files/logs/app_log_current.txt"

# Или скопировать на компьютер
adb exec-out run-as com.example.bitrix_app cat /data/data/com.example.bitrix_app/files/logs/app_log_current.txt > app_log.txt
```

#### Получить crash reports:

```bash
adb shell "run-as com.example.bitrix_app cat /data/data/com.example.bitrix_app/files/last_crash.txt"
```

### 🔄 Автоматизация Обновления

**Скрипт для автоматического обновления:**

Создайте файл `update_app.sh`:

```bash
#!/bin/bash

DEVICE_IP="<IP_АДРЕС_ПЛАНШЕТА>"
PACKAGE_NAME="com.example.bitrix_app"

echo "Подключение к устройству..."
adb connect $DEVICE_IP:5555

echo "Скачивание последней версии..."
wget -q https://github.com/shribusson/ksm_android_app/releases/latest/download/app-release.apk -O app-release.apk

echo "Установка APK..."
adb install -r app-release.apk

echo "Перезапуск приложения..."
adb shell am force-stop $PACKAGE_NAME
sleep 2
adb shell am start -n $PACKAGE_NAME/.MainActivity

echo "Готово! Проверка логов..."
adb logcat | grep Bitrix
```

Сделайте скрипт исполняемым:
```bash
chmod +x update_app.sh
./update_app.sh
```

## 🏗️ Локальная Разработка

### Требования
- JDK 17
- Android SDK (API 34)
- Android Studio (опционально)

### Сборка

```bash
# Debug версия
./gradlew assembleDebug

# Release версия
./gradlew assembleRelease

# Запустить тесты
./gradlew test

# Установить на подключенное устройство
./gradlew installDebug
```

### Структура Проекта

```
app/
├── src/
│   ├── main/
│   │   ├── java/com/example/bitrix_app/
│   │   │   ├── data/          # Repository, API, Database
│   │   │   ├── domain/        # Use Cases, Models
│   │   │   ├── presentation/  # UI, ViewModels
│   │   │   └── di/            # Dependency Injection
│   │   └── res/               # Resources
│   └── test/                  # Unit Tests
└── build.gradle.kts
```

## 📝 Конфигурация

### Keystore для Release

Release APK подписывается ключом из файла `app/release.keystore`. 

**Параметры по умолчанию:**
- Store Password: `android`
- Key Alias: `key0`
- Key Password: `android`

⚠️ **Важно:** Для production использования создайте новый keystore:

```bash
keytool -genkey -v -keystore app/release.keystore -alias key0 -keyalg RSA -keysize 2048 -validity 10000
```

## 🔐 Безопасность

- Логи сохраняются локально на устройстве
- Используется EncryptedPreferences для хранения webhooks
- Приложение работает в Lock Task Mode (kiosk mode)
- Автоматический перезапуск при крашах

## 📱 Режим Киоска

Подробная инструкция по настройке планшета в режиме киоска: [KIOSK_SETUP.md](KIOSK_SETUP.md)

## 📄 Лицензия

Proprietary - Все права защищены.
