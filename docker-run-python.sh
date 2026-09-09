#!/bin/bash

# Script para construir e executar o container Python

IMAGE_NAME="sftp-monitor-python"
CONTAINER_NAME="sftp-monitor-python-app"

echo "=== Construindo imagem Python Docker ==="
docker build -f Dockerfile-python -t $IMAGE_NAME .

if [ $? -eq 0 ]; then
    echo "✓ Imagem Python construída com sucesso!"
    
    echo ""
    echo "=== Executando container Python ==="
    
    # Parar container anterior se existir
    docker stop $CONTAINER_NAME 2>/dev/null
    docker rm $CONTAINER_NAME 2>/dev/null
    
    # Executar container
    docker run -d \
        --name $CONTAINER_NAME \
        --restart unless-stopped \
        -v $(pwd)/downloads:/app/downloads \
        $IMAGE_NAME
    
    echo "✓ Container Python iniciado!"
    echo ""
    echo "Para ver logs: docker logs -f $CONTAINER_NAME"
    echo "Para parar: docker stop $CONTAINER_NAME"
    
else
    echo "❌ Erro ao construir imagem Python!"
    exit 1
fi
