#!/bin/bash

# Скрипт для автоматической замены иконки приложения
# Исходный файл: icon.png в корне проекта

if [ ! -f "icon.png" ]; then
    echo "Ошибка: файл icon.png не найден в корне проекта."
    exit 1
fi

# Удаляем адаптивные иконки (XML), чтобы принудительно использовать PNG
rm -rf app/src/main/res/mipmap-anydpi-v26

# Копируем icon.png во все папки mipmap как ic_launcher.png и ic_launcher_round.png
for density in mdpi hdpi xhdpi xxhdpi xxxhdpi; do
    mkdir -p "app/src/main/res/mipmap-$density"
    cp "icon.png" "app/src/main/res/mipmap-$density/ic_launcher.png"
    cp "icon.png" "app/src/main/res/mipmap-$density/ic_launcher_round.png"
done

echo "✅ Иконка приложения обновлена из icon.png"