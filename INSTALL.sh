#!/bin/bash

# Instruções de instalação do SFTP Monitor Docker

echo "=== Instalação SFTP Monitor Docker ==="
echo ""

# Verificar se Docker está instalado
if ! command -v docker &> /dev/null; then
    echo "❌ Docker não encontrado!"
    echo ""
    echo "Para instalar Docker:"
    echo "  curl -fsSL https://get.docker.com -o get-docker.sh"
    echo "  sudo sh get-docker.sh"
    echo "  sudo usermod -aG docker \$USER"
    echo "  # Reiniciar sessão após adicionar ao grupo"
    echo ""
    exit 1
fi

echo "✓ Docker encontrado: $(docker --version)"
echo ""

# Dar permissões aos scripts
chmod +x docker-run.sh docker-monitor.sh

echo "=== Opções de uso ==="
echo ""
echo "1. Executar uma vez:"
echo "   ./docker-run.sh"
echo ""
echo "2. Monitoramento contínuo (5 em 5 minutos):"
echo "   ./docker-monitor.sh &"
echo ""
echo "3. Ver logs:"
echo "   docker logs -f sftp-monitor-app"
echo ""
echo "4. Parar:"
echo "   docker stop sftp-monitor-app"
echo ""
echo "5. Executar manualmente:"
echo "   docker run --rm -v \$(pwd)/downloads:/app/downloads sftp-monitor"
echo ""
echo "Os arquivos baixados ficarão na pasta 'downloads/'"
