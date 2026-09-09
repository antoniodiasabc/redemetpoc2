#!/bin/bash

echo "🚀 Subindo POC SIGMET..."

# Compilar projeto
echo "📦 Compilando projeto..."
mvn compile -q

# Matar processos existentes
echo "🔄 Parando processos existentes..."
pkill -f PocSigmetApplication

# Aguardar um pouco
sleep 2

# Subir Spring Boot
echo "🌟 Iniciando Spring Boot..."
nohup java -Djava.library.path=libs -Dnu.pattern.OpenCV.loader.enabled=true -cp "target/classes:libs/*" com.pocsigmet.PocSigmetApplication > app.log 2>&1 &

# Aguardar inicialização
sleep 3

# Verificar se subiu
if curl -s http://localhost:8082/health > /dev/null 2>&1; then
    echo "✅ Spring Boot rodando!"
    echo "🌐 Acesse: http://localhost:8082"
else
    echo "❌ Erro ao iniciar Spring Boot"
    exit 1
fi

echo "🎯 Sistema pronto!"
