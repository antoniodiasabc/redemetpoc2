#!/bin/bash

# Script para construir e executar o container Docker

IMAGE_NAME="sftp-monitor"
CONTAINER_NAME="sftp-monitor-app"

echo "=== Construindo imagem Docker ==="
docker build -t $IMAGE_NAME .

if [ $? -eq 0 ]; then
    echo "✓ Imagem construída com sucesso!"
    
    echo ""
    echo "=== Executando container ==="
    
    # Parar container anterior se existir
    docker stop $CONTAINER_NAME 2>/dev/null
    docker rm $CONTAINER_NAME 2>/dev/null
    
    # Executar container
    docker run -d \
        --name $CONTAINER_NAME \
        --restart unless-stopped \
        -v $(pwd)/downloads:/app/downloads \
        $IMAGE_NAME
    
    echo "✓ Container iniciado!"
    echo ""
    echo "Para ver logs: docker logs -f $CONTAINER_NAME"
    echo "Para parar: docker stop $CONTAINER_NAME"
    echo "Para executar uma vez: docker run --rm -v \$(pwd)/downloads:/app/downloads $IMAGE_NAME"
    
else
    echo "❌ Erro ao construir imagem!"
    exit 1
fi
