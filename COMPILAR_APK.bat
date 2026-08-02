@echo off
chcp 65001 >nul
title Compilar APK - SMS Forwarder v2
cd /d "%~dp0"

echo ============================================
echo   COMPILANDO O APK - SMS Forwarder
echo ============================================
echo.

set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
if not exist "%JAVA_HOME%\bin\java.exe" set "JAVA_HOME=%LOCALAPPDATA%\Programs\Android Studio\jbr"
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo ERRO: Nao achei o Java do Android Studio.
    pause
    exit /b 1
)
echo Java: %JAVA_HOME%

REM cria local.properties apontando pro SDK, se faltar
if not exist "local.properties" (
    echo Criando local.properties...
    > local.properties echo sdk.dir=%LOCALAPPDATA:\=\\%\\Android\\Sdk
)
echo SDK apontado em local.properties
echo.

if not exist "gradle\wrapper\gradle-wrapper.jar" (
    echo Baixando componente do Gradle...
    if not exist "gradle\wrapper" mkdir "gradle\wrapper"
    curl -L -o gradle\wrapper\gradle-wrapper.jar https://raw.githubusercontent.com/gradle/gradle/v8.2.0/gradle/wrapper/gradle-wrapper.jar
)

echo Compilando... (primeira vez demora uns minutos)
echo.
call gradlew.bat assembleDebug

echo.
if exist "app\build\outputs\apk\debug\app-debug.apk" (
    echo ============================================
    echo   PRONTO! APK gerado com sucesso!
    echo ============================================
    echo Arquivo: app\build\outputs\apk\debug\app-debug.apk
    explorer app\build\outputs\apk\debug
) else (
    echo Algo deu errado. Veja as mensagens acima.
)
echo.
pause
