#!/bin/bash

# Monitor SFTP Java - executa a cada 5 minutos (compatível Java 8+)

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_FILE="$SCRIPT_DIR/sftp_monitor_java.log"

echo "=== Iniciando monitoramento SFTP Java ===" | tee -a "$LOG_FILE"
echo "Diretório: $SCRIPT_DIR" | tee -a "$LOG_FILE"
echo "Log: $LOG_FILE" | tee -a "$LOG_FILE"
echo "Pressione Ctrl+C para parar" | tee -a "$LOG_FILE"
echo "" | tee -a "$LOG_FILE"

# Verificar se está compilado
if [ ! -f "$SCRIPT_DIR/SftpMonitor.class" ]; then
    echo "Classe não compilada. Execute: ./compile_java.sh" | tee -a "$LOG_FILE"
    exit 1
fi

# Verificar se tem a biblioteca JSch
if [ ! -f "$SCRIPT_DIR/jsch-0.1.55.jar" ]; then
    echo "Biblioteca JSch não encontrada. Execute: ./compile_java.sh" | tee -a "$LOG_FILE"
    exit 1
fi

# Função para executar o download
run_download() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] Verificando novos arquivos..." | tee -a "$LOG_FILE"
    cd "$SCRIPT_DIR"
    java -cp ".:jsch-0.1.55.jar" SftpMonitor 2>&1 | tee -a "$LOG_FILE"
    echo "" | tee -a "$LOG_FILE"
}

# Trap para capturar Ctrl+C
trap 'echo "Monitoramento Java interrompido." | tee -a "$LOG_FILE"; exit 0' INT

# Loop principal - executa a cada 5 minutos (300 segundos)
while true; do
    run_download
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] Próxima verificação em 5 minutos..." | tee -a "$LOG_FILE"
    sleep 300
done
