#!/bin/bash

# Script para monitorar e baixar arquivos SFTP a cada 5 minutos
# Executa: ./monitor_sftp.sh

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_FILE="$SCRIPT_DIR/sftp_monitor.log"

echo "=== Iniciando monitoramento SFTP ===" | tee -a "$LOG_FILE"
echo "Diretório: $SCRIPT_DIR" | tee -a "$LOG_FILE"
echo "Log: $LOG_FILE" | tee -a "$LOG_FILE"
echo "Pressione Ctrl+C para parar" | tee -a "$LOG_FILE"
echo "" | tee -a "$LOG_FILE"

# Função para executar o download
run_download() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] Verificando novos arquivos..." | tee -a "$LOG_FILE"
    cd "$SCRIPT_DIR"
    python3 sftp_monitor.py 2>&1 | tee -a "$LOG_FILE"
    echo "" | tee -a "$LOG_FILE"
}

# Trap para capturar Ctrl+C
trap 'echo "Monitoramento interrompido." | tee -a "$LOG_FILE"; exit 0' INT

# Loop principal - executa a cada 5 minutos (300 segundos)
while true; do
    run_download
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] Próxima verificação em 5 minutos..." | tee -a "$LOG_FILE"
    sleep 300
done
