#!/bin/bash
# Icon generator using sips (Bash 3.2 compatible)
BASE_ICON="/Users/void/StudioProjects/bitrix_app_android/icon.png"
RES_DIR="/Users/void/StudioProjects/bitrix_app_android/app/src/main/res"

if [ ! -f "$BASE_ICON" ]; then
    echo "Error: icon.png not found at $BASE_ICON"
    exit 1
fi

# Function to generate icons
generate_icon() {
    local folder=$1
    local size=$2
    local target_dir="$RES_DIR/$folder"
    
    mkdir -p "$target_dir"
    echo "Generating $size x $size to $folder"
    
    sips -z $size $size "$BASE_ICON" --out "$target_dir/ic_launcher.png"
    sips -z $size $size "$BASE_ICON" --out "$target_dir/ic_launcher_round.png"
    sips -z $size $size "$BASE_ICON" --out "$target_dir/ic_launcher_foreground.png"
}

generate_icon "mipmap-mdpi" 48
generate_icon "mipmap-hdpi" 72
generate_icon "mipmap-xhdpi" 96
generate_icon "mipmap-xxhdpi" 144
generate_icon "mipmap-xxxhdpi" 192

echo "Icons generated."