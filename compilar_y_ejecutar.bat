@echo off
REM ============================================================
REM  Script de compilacion y ejecucion - Sistema de Emergencias
REM  IUDigital de Antioquia - EA2
REM ============================================================

set JAVA_HOME=C:\Program Files\Java\jdk-21
set JAVA="%JAVA_HOME%\bin\java.exe"
set MVN="C:\Program Files\netbeans\java\maven\bin\mvn.cmd"
set JAR_NAME=sistema-emergencias.jar

echo.
echo  ============================================
echo   Sistema de Gestion de Emergencias Medicas
echo   Compilando con Maven...
echo  ============================================

call %MVN% clean package -DskipTests
if %errorlevel% neq 0 (
    echo ERROR: Fallo la compilacion con Maven.
    exit /b 1
)

copy /Y target\%JAR_NAME% %JAR_NAME% >nul
echo  Compilacion exitosa y JAR generado: %JAR_NAME%

echo.
echo  Iniciando simulacion...
echo  ============================================
%JAVA% -jar %JAR_NAME% %*
