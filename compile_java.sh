#!/bin/bash

# Script para compilar o monitor SFTP Java

JAVA_FILE="SftpMonitor.java"

# Verificar e corrigir permissões
echo "Verificando permissões..."
chmod 755 .
chmod 644 *.java *.jar 2>/dev/null

# Compilar
echo "Compilando $JAVA_FILE..."
javac -cp "jsch-0.1.55.jar" "$JAVA_FILE"

if [ $? -eq 0 ]; then
    echo "✓ Compilação concluída!"
    
    # Dar permissão de execução
    chmod 755 *.class 2>/dev/null
    
    # Testar execução
    echo "Testando..."
    java -cp ".:jsch-0.1.55.jar" SftpMonitor
    
    echo ""
    echo "Para executar: java -cp \".:jsch-0.1.55.jar\" SftpMonitor"
    echo "Para monitorar: ./monitor_sftp_java.sh &"
else
    echo "❌ Erro na compilação!"
    echo "Tentando em diretório temporário..."
    
    # Fallback: usar /tmp
    TEMP_DIR="/tmp/sftp_compile_$$"
    mkdir -p "$TEMP_DIR"
    cp *.java *.jar "$TEMP_DIR/" 2>/dev/null
    cd "$TEMP_DIR"
    
    javac -cp "jsch-0.1.55.jar" "$JAVA_FILE"
    if [ $? -eq 0 ]; then
        echo "✓ Compilado em $TEMP_DIR"
        cp *.class "$OLDPWD/"
        cd "$OLDPWD"
        rm -rf "$TEMP_DIR"
    else
        echo "❌ Falha total na compilação!"
        exit 1
    fi
fi
