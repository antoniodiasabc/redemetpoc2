#!/bin/bash

echo "🚀 Subindo POC SIGMET com HSV corrigido..."

# Compilar projeto
echo "📦 Compilando projeto..."
mvn compile -q

# Matar processos existentes
echo "🔄 Parando processos existentes..."
pkill -f PocSigmetApplication

# Aguardar um pouco
sleep 2

# Configurar biblioteca OpenCV
export OPENCV_LIB_PATH="/home/aodias/pocsigmet-spring-final/libs"
export LD_LIBRARY_PATH="$OPENCV_LIB_PATH:$LD_LIBRARY_PATH"

# Subir Spring Boot com configuração OpenCV
echo "🌟 Iniciando Spring Boot com OpenCV..."
nohup mvn exec:java \
  -Dexec.mainClass="com.pocsigmet.PocSigmetApplication" \
  -Djava.library.path="$OPENCV_LIB_PATH" \
  -Dnu.pattern.OpenCV.loader.enabled=true \
  > app.log 2>&1 &

# Aguardar inicialização
sleep 5

# Verificar se subiu
if pgrep -f "exec:java" > /dev/null; then
    echo "✅ Spring Boot rodando!"
    echo "🌐 Acesse: http://localhost:8082"
    echo "🔍 HSV Simple deve estar funcionando agora"
else
    echo "❌ Erro ao iniciar Spring Boot"
    tail -20 app.log
    exit 1
fi

echo "🎯 Sistema pronto com HSV!"
