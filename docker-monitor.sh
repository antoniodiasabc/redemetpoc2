#!/bin/bash

# Script para monitoramento contínuo com Docker

IMAGE_NAME="sftp-monitor"
LOG_FILE="docker-sftp-monitor.log"

echo "=== Monitor SFTP Docker ===" | tee -a "$LOG_FILE"
echo "Iniciado em: $(date)" | tee -a "$LOG_FILE"
echo "Pressione Ctrl+C para parar" | tee -a "$LOG_FILE"
echo "" | tee -a "$LOG_FILE"

# Função para executar o download
run_download() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] Executando verificação..." | tee -a "$LOG_FILE"
    
    docker run --rm \
        -v $(pwd)/downloads:/app/downloads \
        -v $(pwd)/.downloaded_files.txt:/app/.downloaded_files.txt \
        $IMAGE_NAME 2>&1 | tee -a "$LOG_FILE"
    
    echo "" | tee -a "$LOG_FILE"
}

# Trap para capturar Ctrl+C
trap 'echo "Monitor Docker interrompido." | tee -a "$LOG_FILE"; exit 0' INT

# Loop principal - executa a cada 5 minutos
while true; do
    run_download
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] Próxima verificação em 5 minutos..." | tee -a "$LOG_FILE"
    sleep 300
done
