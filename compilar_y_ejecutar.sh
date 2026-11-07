#!/bin/bash
# ============================================================
#  Script de compilacion y ejecucion - Sistema de Emergencias
#  IUDigital de Antioquia - EA2
# ============================================================

JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk}"
JAVAC="$JAVA_HOME/bin/javac"
JAR_TOOL="$JAVA_HOME/bin/jar"
JAVA_CMD="$JAVA_HOME/bin/java"
OUT_DIR="out"
JAR_NAME="sistema-emergencias.jar"

echo ""
echo " ============================================"
echo "  Sistema de Gestion de Emergencias Medicas"
echo "  Compilando..."
echo " ============================================"

mkdir -p "$OUT_DIR"

# Recolectar fuentes
find src -name "*.java" > sources.txt

# Compilar
"$JAVAC" -d "$OUT_DIR" @sources.txt
if [ $? -ne 0 ]; then
    echo "ERROR: Fallo la compilacion."
    rm sources.txt
    exit 1
fi
rm sources.txt

echo " Compilacion exitosa!"

# Crear MANIFEST
echo "Main-Class: com.emergencias.Main" > "$OUT_DIR/MANIFEST.MF"

# Empaquetar
"$JAR_TOOL" cfm "$JAR_NAME" "$OUT_DIR/MANIFEST.MF" -C "$OUT_DIR" .
echo " JAR generado: $JAR_NAME"

echo ""
echo " Iniciando simulacion..."
echo " ============================================"
"$JAVA_CMD" -jar "$JAR_NAME"

