#!/bin/bash

echo "🛑 Parando POC SIGMET..."

# Matar Spring Boot
echo "🔄 Parando Spring Boot..."
pkill -f PocSigmetApplication

# Aguardar um pouco
sleep 2

# Verificar se parou
if pgrep -f PocSigmetApplication > /dev/null; then
    echo "⚠️ Forçando parada..."
    pkill -9 -f PocSigmetApplication
    sleep 1
fi

if pgrep -f PocSigmetApplication > /dev/null; then
    echo "❌ Erro ao parar processo"
else
    echo "✅ Todos os processos parados!"
fi
