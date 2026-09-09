#!/usr/bin/env python3
import paramiko
import os
from pathlib import Path

def download_sftp_files():
    # Configurações
    hostname = "40.88.147.159"
    username = "AS_WX_DECEA"
    key_file = os.path.expanduser("~/.ssh/decea_key")
    local_dir = "./downloads"
    
    # Criar diretório local se não existir
    Path(local_dir).mkdir(exist_ok=True)
    
    try:
        # Conectar SFTP
        ssh = paramiko.SSHClient()
        ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
        
        # Carregar chave privada
        private_key = paramiko.RSAKey.from_private_key_file(key_file)
        
        # Conectar
        ssh.connect(hostname, username=username, pkey=private_key)
        sftp = ssh.open_sftp()
        
        print(f"Conectado ao servidor {hostname}")
        
        # Listar arquivos no diretório remoto
        remote_files = sftp.listdir('.')
        print(f"Encontrados {len(remote_files)} arquivos:")
        
        for file in remote_files:
            print(f"  - {file}")
            
        # Baixar todos os arquivos
        for file in remote_files:
            try:
                local_path = os.path.join(local_dir, file)
                sftp.get(file, local_path)
                print(f"✓ Baixado: {file}")
            except Exception as e:
                print(f"✗ Erro ao baixar {file}: {e}")
        
        # Fechar conexões
        sftp.close()
        ssh.close()
        print("Download concluído!")
        
    except Exception as e:
        print(f"Erro de conexão: {e}")

if __name__ == "__main__":
    download_sftp_files()
