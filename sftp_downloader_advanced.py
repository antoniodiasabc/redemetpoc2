#!/usr/bin/env python3
import paramiko
import os
import sys
from pathlib import Path
import argparse
from datetime import datetime

def download_sftp_files(remote_dir=".", local_dir="./downloads", file_pattern="*"):
    # Configurações
    hostname = "40.88.147.159"
    username = "AS_WX_DECEA"
    key_file = os.path.expanduser("~/.ssh/decea_key")
    
    # Criar diretório local se não existir
    Path(local_dir).mkdir(parents=True, exist_ok=True)
    
    try:
        # Conectar SFTP
        ssh = paramiko.SSHClient()
        ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
        
        # Carregar chave privada
        private_key = paramiko.RSAKey.from_private_key_file(key_file)
        
        # Conectar
        ssh.connect(hostname, username=username, pkey=private_key)
        sftp = ssh.open_sftp()
        
        print(f"✓ Conectado ao servidor {hostname}")
        print(f"✓ Diretório remoto: {remote_dir}")
        print(f"✓ Diretório local: {local_dir}")
        
        # Navegar para diretório remoto
        sftp.chdir(remote_dir)
        
        # Listar arquivos
        remote_files = sftp.listdir('.')
        
        # Filtrar arquivos se necessário
        if file_pattern != "*":
            import fnmatch
            remote_files = [f for f in remote_files if fnmatch.fnmatch(f, file_pattern)]
        
        print(f"✓ Encontrados {len(remote_files)} arquivos:")
        
        # Mostrar arquivos
        for file in remote_files:
            try:
                file_stat = sftp.stat(file)
                size = file_stat.st_size
                mod_time = datetime.fromtimestamp(file_stat.st_mtime)
                print(f"  - {file} ({size} bytes, {mod_time.strftime('%Y-%m-%d %H:%M')})")
            except:
                print(f"  - {file}")
        
        # Confirmar download
        if remote_files:
            response = input(f"\nBaixar {len(remote_files)} arquivos? (s/N): ")
            if response.lower() not in ['s', 'sim', 'y', 'yes']:
                print("Download cancelado.")
                return
        
        # Baixar arquivos
        downloaded = 0
        for file in remote_files:
            try:
                local_path = os.path.join(local_dir, file)
                
                # Verificar se arquivo já existe
                if os.path.exists(local_path):
                    response = input(f"Arquivo {file} já existe. Sobrescrever? (s/N): ")
                    if response.lower() not in ['s', 'sim', 'y', 'yes']:
                        print(f"⏭ Pulado: {file}")
                        continue
                
                sftp.get(file, local_path)
                downloaded += 1
                print(f"✓ Baixado: {file}")
                
            except Exception as e:
                print(f"✗ Erro ao baixar {file}: {e}")
        
        # Fechar conexões
        sftp.close()
        ssh.close()
        
        print(f"\n🎉 Download concluído! {downloaded} arquivos baixados em {local_dir}")
        
    except Exception as e:
        print(f"❌ Erro de conexão: {e}")
        return False
    
    return True

def main():
    parser = argparse.ArgumentParser(description="Download de arquivos via SFTP")
    parser.add_argument("--remote-dir", "-r", default=".", help="Diretório remoto (padrão: .)")
    parser.add_argument("--local-dir", "-l", default="./downloads", help="Diretório local (padrão: ./downloads)")
    parser.add_argument("--pattern", "-p", default="*", help="Padrão de arquivos (ex: *.txt)")
    
    args = parser.parse_args()
    
    download_sftp_files(args.remote_dir, args.local_dir, args.pattern)

if __name__ == "__main__":
    main()
