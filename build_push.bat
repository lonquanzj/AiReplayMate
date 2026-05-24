@echo off
cd /d E:\code\AiReplayMate
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot
set PATH=%JAVA_HOME%\bin;%PATH%
echo Building APK...
call gradlew.bat :app:assembleDebug --no-daemon
if %ERRORLEVEL% NEQ 0 (
    echo Build failed!
    exit /b %ERRORLEVEL%
)
echo Build success, installing...
set ADB=C:\Users\lonqu\AppData\Local\Android\Sdk\platform-tools\adb.exe
%ADB% install -r android/app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
if %ERRORLEVEL% NEQ 0 (
    echo Install failed!
    exit /b %ERRORLEVEL%
)
echo Done!
