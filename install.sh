#!/bin/bash

# Script de instalação do SFTP Monitor
# Execute no servidor: ./install.sh

echo "=== Instalação do SFTP Monitor ==="

# Criar diretório .ssh se não existir
mkdir -p ~/.ssh
chmod 700 ~/.ssh

# Definir permissões da chave SSH
if [ -f "home/aodias/.ssh/decea_key" ]; then
    cp "home/aodias/.ssh/decea_key" ~/.ssh/
    chmod 600 ~/.ssh/decea_key
    echo "✓ Chave SSH instalada"
fi

# Tornar scripts executáveis
chmod +x *.sh *.py

echo "✓ Permissões configuradas"

# Verificar dependências Python
if command -v python3 &> /dev/null; then
    echo "✓ Python 3 encontrado"
    if python3 -c "import paramiko" 2>/dev/null; then
        echo "✓ Paramiko já instalado"
    else
        echo "⚠ Instalando paramiko..."
        pip3 install paramiko
    fi
else
    echo "❌ Python 3 não encontrado. Instale: sudo apt install python3 python3-pip"
fi

# Verificar Java
if command -v java &> /dev/null; then
    echo "✓ Java encontrado: $(java -version 2>&1 | head -1)"
else
    echo "❌ Java não encontrado. Instale: sudo apt install openjdk-11-jdk"
fi

echo ""
echo "=== Instalação concluída! ==="
echo ""
echo "Para usar:"
echo "  Python: ./monitor_sftp.sh &"
echo "  Java:   ./monitor_sftp_java.sh &"
echo ""
echo "Para testar:"
echo "  Python: python3 sftp_monitor.py"
echo "  Java:   java -cp \".:./libs/*\" SftpMonitor"
