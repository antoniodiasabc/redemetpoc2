#!/bin/bash

echo "🚀 Subindo OpmetSwingClient..."

# Matar processos existentes
echo "🔄 Parando processos existentes..."
pkill -f OpmetSwingClient

# Aguardar um pouco
sleep 2

# Compilar
echo "📦 Compilando..."
javac OpmetSwingClient.java

# Subir em background
echo "🌟 Iniciando OpmetSwingClient..."
nohup java OpmetSwingClient > opmet.log 2>&1 &

# Aguardar inicialização
sleep 3

# Verificar se subiu
if pgrep -f OpmetSwingClient > /dev/null; then
    echo "✅ OpmetSwingClient rodando!"
    echo "📋 Logs: tail -f opmet.log"
else
    echo "❌ Erro ao iniciar OpmetSwingClient"
    exit 1
fi

echo "🎯 Cliente OPMET pronto!"
