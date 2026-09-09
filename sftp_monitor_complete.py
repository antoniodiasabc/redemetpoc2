#!/usr/bin/env python3
import paramiko
import os
import time
import requests
from datetime import datetime
import socket

# Configurações
HOSTNAME = "40.88.147.159"
USERNAME = "AS_WX_DECEA"
KEY_FILE = "/root/.ssh/decea_key"
KEY_PASSPHRASE = "DeceaAmdar-67K"  # Senha da chave SSH
LOCAL_DIR = "downloads"
CONTROL_FILE = ".downloaded_files.txt"

def get_public_ip():
    """Obter IP público com múltiplos serviços"""
    services = [
        "https://api.ipify.org",
        "https://ipinfo.io/ip", 
        "https://icanhazip.com",
        "https://ident.me"
    ]
    
    for service in services:
        try:
            response = requests.get(service, timeout=3)
            if response.status_code == 200:
                ip = response.text.strip()
                # Validar IP
                if ip.count('.') == 3 and all(part.isdigit() for part in ip.split('.')):
                    return ip
        except:
            continue
    return "IP não disponível"

def load_downloaded_files():
    """Carregar lista de arquivos já baixados"""
    try:
        with open(CONTROL_FILE, 'r') as f:
            return set(line.strip() for line in f)
    except FileNotFoundError:
        return set()

def save_downloaded_files(files):
    """Salvar lista de arquivos baixados"""
    try:
        with open(CONTROL_FILE, 'w') as f:
            for file in files:
                f.write(file + '\n')
    except Exception as e:
        print(f"Erro ao salvar controle: {e}")

def download_new_files():
    """Baixar novos arquivos do SFTP"""
    timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    
    # Mostrar informações iniciais
    print("=== SFTP Monitor Python ===")
    print(f"Timestamp: {timestamp}")
    print(f"IP de saída: {get_public_ip()}")
    print(f"Servidor SFTP: {HOSTNAME}")
    print(f"Usuário: {USERNAME}")
    print("=" * 30)
    
    try:
        # Criar diretório local
        os.makedirs(LOCAL_DIR, exist_ok=True)
        
        # Carregar arquivos já baixados
        downloaded_files = load_downloaded_files()
        
        # Configurar SSH
        ssh = paramiko.SSHClient()
        ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
        
        print(f"Conectando em {HOSTNAME}:22...")
        
        # Tentar conectar
        ssh.connect(
            hostname=HOSTNAME,
            port=22,
            username=USERNAME,
            key_filename=KEY_FILE,
            passphrase=KEY_PASSPHRASE,  # Senha da chave SSH
            timeout=10,
            banner_timeout=10
        )
        
        print("✓ Conexão SSH estabelecida!")
        
        # Abrir canal SFTP
        sftp = ssh.open_sftp()
        print("✓ Canal SFTP aberto!")
        
        # Listar arquivos remotos
        files = sftp.listdir_attr('.')
        new_files = 0
        
        for file_attr in files:
            filename = file_attr.filename
            
            # Filtrar apenas .txt e .xml
            if not (filename.endswith('.txt') or filename.endswith('.xml')):
                continue
            
            # Criar chave única do arquivo
            file_key = f"{filename}_{file_attr.st_size}_{file_attr.st_mtime}"
            
            if file_key not in downloaded_files:
                try:
                    local_path = os.path.join(LOCAL_DIR, filename)
                    sftp.get(filename, local_path)
                    downloaded_files.add(file_key)
                    new_files += 1
                    print(f"✓ Baixado: {filename} ({file_attr.st_size} bytes)")
                except Exception as e:
                    print(f"❌ Erro ao baixar {filename}: {e}")
        
        # Salvar controle
        save_downloaded_files(downloaded_files)
        
        if new_files > 0:
            print(f"✓ {new_files} arquivo(s) novo(s) baixado(s)!")
        else:
            print("ℹ Nenhum arquivo novo encontrado")
        
        sftp.close()
        ssh.close()
        
    except paramiko.AuthenticationException as e:
        print(f"[{timestamp}] ❌ Erro: Falha na autenticação SSH")
        print(f"   Detalhes: {e}")
        print(f"   Verifique: chave SSH ({KEY_FILE}), usuário ({USERNAME})")
        
    except paramiko.SSHException as e:
        error_msg = str(e).lower()
        if "connection refused" in error_msg:
            print(f"[{timestamp}] ❌ Erro: Conexão recusada - Firewall bloqueando porta 22")
        elif "timeout" in error_msg or "timed out" in error_msg:
            print(f"[{timestamp}] ❌ Erro: Timeout de conexão (10s) - Rede lenta ou firewall")
        elif "no route to host" in error_msg:
            print(f"[{timestamp}] ❌ Erro: Sem rota para o host")
        else:
            print(f"[{timestamp}] ❌ Erro SSH: {e}")
        print(f"   Detalhes: {e}")
        
    except socket.timeout:
        print(f"[{timestamp}] ❌ Erro: Timeout de socket - Firewall ou rede lenta")
        
    except socket.gaierror as e:
        print(f"[{timestamp}] ❌ Erro: Não conseguiu resolver hostname")
        print(f"   Detalhes: {e}")
        print(f"   Verifique: DNS, conectividade com {HOSTNAME}")
        
    except Exception as e:
        print(f"[{timestamp}] ❌ Erro geral: {e}")
        print(f"   Tipo: {type(e).__name__}")

def main():
    """Loop principal de monitoramento"""
    print("=== SFTP Monitor Python Iniciado ===")
    print("Monitoramento a cada 5 minutos...")
    print("Pressione Ctrl+C para parar")
    print()
    
    while True:
        try:
            download_new_files()
            next_check = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
            print(f"[{next_check}] Próxima verificação em 5 minutos...")
            print()
            time.sleep(300)  # 5 minutos
            
        except KeyboardInterrupt:
            print("Monitor interrompido pelo usuário.")
            break
        except Exception as e:
            print(f"Erro no loop principal: {e}")
            time.sleep(300)  # Aguarda 5 minutos mesmo com erro

if __name__ == "__main__":
    main()
