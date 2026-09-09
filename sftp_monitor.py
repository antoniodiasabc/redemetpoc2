#!/usr/bin/env python3
import paramiko
import os
import json
from pathlib import Path
from datetime import datetime

def load_downloaded_files():
    """Carrega lista de arquivos já baixados"""
    try:
        with open('.downloaded_files.json', 'r') as f:
            return json.load(f)
    except:
        return {}

def save_downloaded_files(files_dict):
    """Salva lista de arquivos baixados"""
    with open('.downloaded_files.json', 'w') as f:
        json.dump(files_dict, f)

def download_new_files():
    hostname = "40.88.147.159"
    username = "AS_WX_DECEA"
    key_file = os.path.expanduser("~/.ssh/decea_key")
    local_dir = "./downloads"
    
    Path(local_dir).mkdir(exist_ok=True)
    
    # Carregar arquivos já baixados
    downloaded_files = load_downloaded_files()
    
    try:
        ssh = paramiko.SSHClient()
        ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
        
        try:
            # Testar conectividade primeiro
            import socket
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            sock.settimeout(5)
            result = sock.connect_ex((hostname, 22))
            sock.close()
            
            if result != 0:
                print(f"[{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}] Erro: Servidor inacessível (firewall bloqueando porta 22)")
                return
            
            # Tentar autenticação
            private_key = paramiko.RSAKey.from_private_key_file(key_file)
            ssh.connect(hostname, username=username, pkey=private_key, timeout=10)
            
        except paramiko.ssh_exception.AuthenticationException:
            print(f"[{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}] Erro: Falha na autenticação (chave ou usuário incorreto)")
            return
        except paramiko.ssh_exception.SSHException as ssh_error:
            print(f"[{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}] Erro SSH: {ssh_error}")
            return
        except Exception as conn_error:
            print(f"[{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}] Erro de conexão: {conn_error}")
            return
        sftp = ssh.open_sftp()
        
        # Listar arquivos remotos
        remote_files = sftp.listdir('.')
        
        # Filtrar apenas .txt e .xml
        filtered_files = [f for f in remote_files if f.endswith(('.txt', '.xml'))]
        
        new_files = 0
        for file in filtered_files:
            try:
                # Verificar se é arquivo novo
                file_stat = sftp.stat(file)
                file_key = f"{file}_{file_stat.st_size}_{file_stat.st_mtime}"
                
                if file_key not in downloaded_files:
                    local_path = os.path.join(local_dir, file)
                    sftp.get(file, local_path)
                    downloaded_files[file_key] = datetime.now().isoformat()
                    new_files += 1
                    
                    # Obter informações do arquivo baixado
                    abs_path = os.path.abspath(local_path)
                    file_size = os.path.getsize(local_path)
                    
                    print(f"✓ Arquivo baixado: {file}")
                    print(f"  └─ Salvo em: {abs_path}")
                    print(f"  └─ Tamanho: {file_size} bytes")
                    
            except Exception as e:
                print(f"✗ Erro ao baixar {file}: {e}")
        
        # Salvar lista atualizada
        save_downloaded_files(downloaded_files)
        
        sftp.close()
        ssh.close()
        
        if new_files == 0:
            print(f"[{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}] Nenhum arquivo novo encontrado")
        else:
            print(f"[{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}] {new_files} novos arquivos baixados")
            
    except Exception as e:
        print(f"[{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}] Erro: {e}")

if __name__ == "__main__":
    download_new_files()
