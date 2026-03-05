@echo off
REM ==============================================================
REM BRZ Lang — Script de execução (Windows)
REM Uso: brz <comando> [opções]
REM ==============================================================

setlocal

set "SCRIPT_DIR=%~dp0"
set "BRZ_JAR=%SCRIPT_DIR%target\brz.jar"

if not exist "%BRZ_JAR%" (
    if exist "%SCRIPT_DIR%brz.jar" (
        set "BRZ_JAR=%SCRIPT_DIR%brz.jar"
    ) else (
        echo [BRZ] JAR nao encontrado. Compilando...
        cd /d "%SCRIPT_DIR%"
        where mvn >nul 2>&1
        if %errorlevel% neq 0 (
            echo [BRZ] Maven nao encontrado. Instale o Maven primeiro.
            exit /b 1
        )
        mvn package -q -DskipTests
        echo [BRZ] Compilado com sucesso!
    )
)

where java >nul 2>&1
if %errorlevel% neq 0 (
    echo [BRZ] Java nao encontrado. Instale o JDK 17+.
    exit /b 1
)

java -jar "%BRZ_JAR%" %*
