#!/bin/bash

echo "🐳 Reconstruindo e subindo Docker..."

# Parar containers
echo "🛑 Parando containers..."
docker-compose down

# Rebuild sem cache (já compila o JAR automaticamente)
echo "🔨 Rebuild da imagem (compilando JAR)..."
docker-compose build --no-cache

# Subir novamente
echo "🚀 Subindo containers..."
docker-compose up -d

# Status
echo "📊 Status dos containers:"
docker-compose ps

echo "✅ JAR compilado e Docker rodando!"
