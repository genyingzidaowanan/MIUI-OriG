#!/bin/bash
set -e
PROJECT_DIR="/mnt/c/Users/DX/Desktop/WSL/Project/hyperorig"

# 工具链：本机已安装 JDK 21（项目原用 22，已统一降到 21），Gradle 由项目 wrapper 自带（8.13，AGP 8.9 所需）
export JAVA_HOME="/usr/lib/jvm/java-21-openjdk-amd64"
export ANDROID_HOME="/home/dx/Android/Sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

cd "$PROJECT_DIR"
bash gradlew :app:assembleDebug --console=plain 2>&1

echo "=== APK ==="
find "$PROJECT_DIR/app/build/outputs" -name "*.apk" -exec ls -lh {} \;
echo "BUILD_DONE"
