# 🔒 Lenovo TB310XU Kiosk Mode Setup

## 📋 Диагностика системы

| Параметр | Значение |
|----------|---------|
| Модель | TB310XU (Lenovo Tab M10 Plus 3rd Gen) |
| Android | 13 (API 33) |
| Статус Device Admin | ✅ Поддерживается |
| Device Owner | ⚠️ Требует перезагрузки в режиме восстановления |
| Процессор | MediaTek (слабое железо) |

---

## 🛠️ Фаза 1: Дегуглинг

### Критические Google-пакеты для отключения (NO BREAK):

```bash
# GMS Core (основное, но осторожно!)
com.google.android.gms

# Play Store и связь
com.android.vending
com.google.android.apps.vending

# Google Services Framework
com.google.android.gsf
com.google.android.gms.location.history

# Google Assistant, Search, Voice
com.google.android.googlequicksearchbox
com.google.android.apps.googleassistant
com.google.android.tts

# Google Sync, Backup, Restore
com.google.android.apps.restore

# Телеметрия
com.google.mainline.telemetry
com.google.android.adservices.api

# На-Play-сервисы (безопасно)
com.google.android.apps.docs
com.google.android.apps.docs.editors.docs
com.google.android.apps.docs.editors.sheets
com.google.android.apps.docs.editors.slides
com.google.android.apps.books
com.google.android.apps.photos
com.google.android.apps.maps
com.google.android.apps.fitness
com.google.android.youtube
com.google.android.apps.youtube.music
com.google.android.apps.messaging
com.google.android.gm
com.google.android.calendar
com.google.android.apps.wellbeing
com.google.android.apps.kids.home
```

### ⚠️ Рисков при отключении GMS:

1. **Может сломаться Wi-Fi** → нужно проверить после отключения
2. **Потеряются push-уведомления** → приложение Bitrix24 может не работать корректно
3. **Падёт система** → если GMS встроен глубоко

**Решение:** Вместо полного отключения GMS используем **partial disable** через pm disable-user.

---

## 🛠️ Фаза 2: Отключение Lenovo-сервисов

### Безопасно отключить:

```bash
# Lenovo Launcher (заменим на собственный)
com.tblenovo.launcher

# Lenovo Setup, Wizard
com.tblenovo.setup
com.tblenovo.lenovowhatsnew

# Lenovo Privacy Dashboard
com.tblenovo.lenovoprivacy

# OTA Updates (обновления будут давить на память)
com.lenovo.ota

# Lenovo Service Framework (телеметрия)
com.lenovo.lsf
com.lenovo.lsf.device

# Lenovo Runtime, DSA
com.lenovo.rt
com.lenovo.dsa

# Lenovo Landscape Vision
com.tblenovo.landscapevision.lenovolandscapevision

# Tab-специфичные вещи
com.tblenovo.center
com.tblenovo.tabpushout
com.tblenovo.soundrecorder

# Lenovo Desktop Launcher
com.tblenovo.desktoplauncher
```

### ⚠️ НЕ трогать (системное):

```bash
com.lenovo.launcher.provider     # Нужен для базовой функции launcher
com.lenovo.ocpl                  # Optimization Controller
com.lenovo.EngineeringCode       # Debug mode
com.aura.oobe.lenovo             # OOBE (может быть скрытым)
```

---

## 🔐 Фаза 3: Kiosk Mode (Device Owner)

### Вариант A: Device Owner (РЕКОМЕНДУЕТСЯ для production)

**Требует:**
1. Полный сброс устройства (factory reset) ИЛИ доступ к Recovery Mode
2. Установка Policy Admin APK (собственное приложение с Device Admin)
3. Конфигурация через ADB

**Преимущества:**
- ✅ Максимальный контроль системы
- ✅ Lock Task Mode (полная блокировка выхода)
- ✅ Отключение системных кнопок
- ✅ Управляемое обновление пакетов

**Недостатки:**
- ❌ Требует factory reset
- ❌ Сложнее восстановление
- ❌ Нужен APK с правами Device Owner

### Вариант B: Device Admin + Launcher (ВРЕМЕННОЕ РЕШЕНИЕ)

**Требует:**
1. Установка собственного launcher'а
2. App Pinning для Bitrix App
3. Device Admin API

**Преимущества:**
- ✅ Без factory reset
- ✅ Быстро развернуть
- ✅ Можно отменить

**Недостатки:**
- ❌ Слабее контроля (пользователь может свайпнуть назад)
- ❌ Системные кнопки видны
- ❌ Нет полной блокировки UI

---

## 💾 Фаза 4: Оптимизация производительности

### Отключение анимаций через ADB:

```bash
# Отключить все анимации (сильно ускорит UI)
adb shell settings put global window_animation_scale 0.0
adb shell settings put global transition_animation_scale 0.0
adb shell settings put global animator_duration_scale 0.0

# Минимальное фоновое затухание
adb shell settings put system screen_brightness 200  # Максимум (255)
adb shell settings put system screen_brightness_mode 0  # Manual

# Энергосбережение - отключить для always-on
adb shell settings put global low_power_mode 0
adb shell settings put global low_power_warning_level 20
```

### Отключение лишних функций:

```bash
# Отключить Bluetooth
adb shell settings put global bluetooth_on 0

# Отключить NFC
adb shell settings put global nfc_on 0

# Отключить GPS
adb shell settings put global location_mode 0

# Отключить автоуобновления
adb shell settings put global automatic_updates_enabled 0

# Отключить уведомления от системы
adb shell settings put global notifications_enabled 0

# Отключить Wi-Fi Sleep Policy (Wi-Fi не будет засыпать)
adb shell settings put global wifi_sleep_policy 2  # = NEVER

# Отключить Screen Timeout (экран всегда включен)
adb shell settings put system screen_off_timeout 2147483647  # Max int
```

### Отключение фоновых сервисов через pm:

```bash
# Chrome (если установлен)
adb shell pm disable-user com.android.chrome

# Различные Google синхро-сервисы
adb shell pm disable-user com.google.android.apps.restore
adb shell pm disable-user com.google.android.apps.wellbeing
adb shell pm disable-user com.google.android.feedback
```

---

## 📱 Фаза 5: Собственное приложение (Bitrix App) — План по пересборке

### Текущий статус
Согласно `CURRENT_STATUS.md`, приложение было стабилизировано, но использует устаревшую архитектуру, где вся логика смешана в `MainActivity`. Для улучшения кодовой базы и надежности подготовлены компоненты для перехода на Clean Architecture.

**Задача «пересборки»**: Интегрировать новую архитектуру для повышения стабильности и упрощения дальнейшей поддержки.

### Компоненты новой архитектуры
- **ViewModels**: `TaskListViewModel`, `TimerViewModel`, `UserSelectionViewModel` для разделения UI-логики.
- **UseCases**: `LoadUsersFromPortalUseCase` для инкапсуляции бизнес-логики.
- **Preferences**: `EncryptedPreferences` для безопасного хранения токенов и webhook.
- **Системные компоненты**: `DeviceOwnerReceiver`, `CrashRecoveryService` для работы в режиме киоска.

### План миграции (рекомендуемый)
1.  **Интегрировать `EncryptedPreferences`**: Заменить прямое использование `SharedPreferences` для хранения чувствительных данных.
2.  **Настроить загрузку пользователей**: Использовать `UserSelectionViewModel` и `LoadUsersFromPortalUseCase` вместо захардкоженных ID пользователей.
3.  **Заменить логику списка задач**: Перенести логику отображения задач из `MainActivity` в `TaskListViewModel`.
4.  **Вынести логику таймера**: Изолировать управление таймерами в `TimerViewModel`.

### Пример интеграции ViewModel
```kotlin
// В MainActivity.kt

// 1. Получить экземпляр ViewModel через делегат ktx
private val taskListViewModel: TaskListViewModel by viewModels()

// 2. В onCreate, подписаться на поток данных (StateFlow/LiveData)
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // ...
    
    lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.STARTED) {
            // Подписка на StateFlow с задачами
            taskListViewModel.tasks.collect { tasks ->
                // Обновить UI (например, LazyColumn)
            }
        }
    }
    
    // Инициировать загрузку задач
    taskListViewModel.loadTasks()
}

// 3. Вся логика загрузки, фильтрации и обработки задач
// теперь находится внутри taskListViewModel, а не в MainActivity.
```

### Ключевые преимущества новой архитектуры
- ✅ **Стабильность**: Четкое управление состоянием, меньше шансов на ошибки.
- ✅ **Тестируемость**: ViewModels и UseCases можно легко покрыть юнит-тестами.
- ✅ **Поддерживаемость**: Код становится чище, структурированнее и проще для понимания.
- ✅ **Безопасность**: `EncryptedPreferences` защищают чувствительные данные, такие как webhook.

---

## 📜 Фаза 6: Главный ADB-скрипт

Создайте файл `setup_kiosk.sh`:

```bash
#!/bin/bash

set -e

DEVICE_SERIAL=${1:-$(adb devices | grep device | head -1 | awk '{print $1}')}

if [ -z "$DEVICE_SERIAL" ]; then
    echo "❌ Устройство не найдено"
    exit 1
fi

echo "🔧 Настройка Kiosk Mode для $DEVICE_SERIAL"
adb -s $DEVICE_SERIAL shell root

# ===== PHASE 1: DISABLE GOOGLE SERVICES =====
echo "📵 Отключение Google-сервисов..."

declare -a GOOGLE_PACKAGES=(
    "com.google.android.googlequicksearchbox"
    "com.google.android.apps.googleassistant"
    "com.google.android.apps.restore"
    "com.google.mainline.telemetry"
    "com.google.android.adservices.api"
    "com.google.android.apps.docs"
    "com.google.android.apps.books"
    "com.google.android.youtube"
    "com.google.android.apps.maps"
    "com.google.android.apps.messaging"
    "com.google.android.gm"
    "com.google.android.apps.wellbeing"
    "com.google.android.apps.kids.home"
)

for pkg in "${GOOGLE_PACKAGES[@]}"; do
    echo "  ⏸️  $pkg"
    adb -s $DEVICE_SERIAL shell pm disable-user $pkg 2>/dev/null || true
done

# ===== PHASE 2: DISABLE LENOVO SERVICES =====
echo "📵 Отключение Lenovo-сервисов..."

declare -a LENOVO_PACKAGES=(
    "com.tblenovo.launcher"
    "com.tblenovo.setup"
    "com.tblenovo.lenovowhatsnew"
    "com.tblenovo.lenovoprivacy"
    "com.lenovo.ota"
    "com.lenovo.lsf"
    "com.lenovo.lsf.device"
    "com.lenovo.rt"
    "com.lenovo.dsa"
    "com.tblenovo.center"
    "com.tblenovo.tabpushout"
    "com.tblenovo.soundrecorder"
    "com.tblenovo.desktoplauncher"
    "com.tblenovo.landscapevision.lenovolandscapevision"
)

for pkg in "${LENOVO_PACKAGES[@]}"; do
    echo "  ⏸️  $pkg"
    adb -s $DEVICE_SERIAL shell pm disable-user $pkg 2>/dev/null || true
done

# ===== PHASE 3: KILL REMAINING BLOAT =====
echo "📵 Отключение прочего bloatware..."

declare -a BLOAT_PACKAGES=(
    "com.google.android.apps.chromecast.app"
    "com.google.android.apps.subscriptions.red"
    "com.google.android.play.games"
    "com.google.android.apps.nbu.files"
    "com.google.android.apps.photos"
    "com.google.android.apps.fitness"
)

for pkg in "${BLOAT_PACKAGES[@]}"; do
    adb -s $DEVICE_SERIAL shell pm disable-user $pkg 2>/dev/null || true
done

# ===== PHASE 4: OPTIMIZE PERFORMANCE =====
echo "⚡ Оптимизация производительности..."

# Отключить анимации
adb -s $DEVICE_SERIAL shell settings put global window_animation_scale 0.0
adb -s $DEVICE_SERIAL shell settings put global transition_animation_scale 0.0
adb -s $DEVICE_SERIAL shell settings put global animator_duration_scale 0.0

# Яркость
adb -s $DEVICE_SERIAL shell settings put system screen_brightness 200
adb -s $DEVICE_SERIAL shell settings put system screen_brightness_mode 0

# Wi-Fi не засыпает
adb -s $DEVICE_SERIAL shell settings put global wifi_sleep_policy 2

# Экран никогда не выключается
adb -s $DEVICE_SERIAL shell settings put system screen_off_timeout 2147483647

# Отключить Bluetooth, NFC, GPS
adb -s $DEVICE_SERIAL shell settings put global bluetooth_on 0
adb -s $DEVICE_SERIAL shell settings put global nfc_on 0
adb -s $DEVICE_SERIAL shell settings put global location_mode 0

echo "✅ Kiosk Mode setup завершён!"
echo ""
echo "📝 Следующие шаги:"
echo "1. Установить Bitrix App: adb install path/to/bitrix_app.apk"
echo "2. Установить Device Owner APK (если нужен полный контроль)"
echo "3. Перезагрузить устройство: adb reboot"
echo ""
echo "🧪 Проверка:"
echo "  adb shell pm list packages | grep disabled"
```

---

## 🔐 Дополнительно: Device Owner Setup (Optional)

Для **полного контроля** нужно создать Device Owner приложение (собственный admin).

Минимальный Policy Admin:

```kotlin
// DeviceAdminReceiver.kt
class MyDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Toast.makeText(context, "Device Admin enabled", Toast.LENGTH_SHORT).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
    }
}
```

Конфигурация в `res/xml/device_admin_receiver.xml`:

```xml
<device-admin xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-policies>
        <lock-task />
        <lock-device />
        <disable-uninstall />
        <disable-keyguard-features />
    </uses-policies>
</device-admin>
```

---

## ❌ Известные ограничения (Lenovo TB310XU)

| Ограничение | Описание | Решение |
|-----------|---------|---------|
| **MediaTek CPU** | Медленный процессор | Отключить анимации, минимизировать фоновые процессы |
| **GMS зависимость** | Wi-Fi может сломаться | Осторожно с отключением GMS, протестировать |
| **Device Owner** | Требует factory reset | Использовать Device Admin временно |
| **Updates** | Может вмешиваться в работу | Отключить OTA полностью |
| **Lenovo OOBE** | Скрытые сервисы | Может требовать разблокировки через ADB |

---

## 📊 Мониторинг и поддержка

### Еженедельная проверка:

```bash
#!/bin/bash
# check_kiosk_health.sh

echo "🔍 Проверка health Kiosk устройства..."

# 1. Включено ли приложение?
adb shell pm list packages | grep com.example.bitrix_app

# 2. Отключены ли Google-сервисы?
adb shell pm list packages -d | grep google | wc -l

# 3. Память
adb shell cat /proc/meminfo | head -5

# 4. Температура (если доступна)
adb shell cat /sys/class/thermal/thermal_zone0/temp

# 5. Логи ошибок
adb shell logcat -d | grep -i error | tail -20

# 6. Запущен ли Bitrix процесс?
adb shell ps | grep com.example.bitrix_app
```

### Что проверять раз в месяц:

- [ ] Приложение стартует после перезагрузки
- [ ] Wi-Fi подключается автоматически
- [ ] Нет повторяющихся ошибок в logcat
- [ ] Память не заполняется (проверить через `adb shell df`)
- [ ] Экран остаётся включен
- [ ] Тепловыделение в норме

---

## 🚨 Emergency: Отката к нормальной системе

Если что-то сломалось:

```bash
# Полный сброс всех отключенных пакетов
adb shell pm enable-user com.google.android.gms
adb shell pm enable-user com.tblenovo.launcher
# ... и т.д.

# Или через ADB Backup/Restore
adb restore backup.ab

# Или факторный сброс (всё потеряется)
adb reboot recovery
# В режиме восстановления выбрать "Factory Reset"
```

---

## 📋 Финальный чек-лист перед production

- [ ] Тестировано на реальном устройстве TB310XU
- [ ] Приложение Bitrix App работает без сбоев 24/7
- [ ] Wi-Fi переподключается после разрыва
- [ ] Нет всплывающих системных диалогов
- [ ] Экран не гаснет
- [ ] Google-сервисы отключены и не перезагружаются
- [ ] Скрипт setup_kiosk.sh идемпотентен (можно запускать несколько раз)
- [ ] Документирован процесс отката
