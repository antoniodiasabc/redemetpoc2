#!/bin/bash

# Script de Build e Deploy - POC SIGMET
# Uso: ./build-deploy.sh [dev|prod|test]

set -e

ENVIRONMENT=${1:-dev}
APP_NAME="pocsigmet-spring-final"
VERSION=$(date +%Y%m%d-%H%M%S)

echo "=== POC SIGMET Build & Deploy ==="
echo "Ambiente: $ENVIRONMENT"
echo "Versão: $VERSION"
echo "================================="

# Função para logging
log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1"
}

# Verificar dependências
check_dependencies() {
    log "Verificando dependências..."
    
    if ! command -v java &> /dev/null; then
        echo "❌ Java não encontrado. Instale Java 11+"
        exit 1
    fi
    
    if ! command -v mvn &> /dev/null; then
        echo "❌ Maven não encontrado. Instale Maven 3.6+"
        exit 1
    fi
    
    if ! command -v docker &> /dev/null; then
        echo "❌ Docker não encontrado. Instale Docker"
        exit 1
    fi
    
    log "✅ Dependências verificadas"
}

# Build da aplicação
build_application() {
    log "Iniciando build da aplicação..."
    
    # Limpar builds anteriores
    mvn clean
    
    # Executar testes
    if [ "$ENVIRONMENT" != "prod" ]; then
        log "Executando testes..."
        mvn test
    fi
    
    # Build do JAR
    log "Compilando aplicação..."
    mvn package -DskipTests
    
    log "✅ Build concluído"
}

# Build da imagem Docker
build_docker_image() {
    log "Construindo imagem Docker..."
    
    docker build -f Dockerfile.optimized -t $APP_NAME:$VERSION .
    docker tag $APP_NAME:$VERSION $APP_NAME:latest
    
    log "✅ Imagem Docker criada: $APP_NAME:$VERSION"
}

# Deploy baseado no ambiente
deploy() {
    case $ENVIRONMENT in
        "dev")
            deploy_development
            ;;
        "prod")
            deploy_production
            ;;
        "test")
            deploy_test
            ;;
        *)
            echo "❌ Ambiente inválido: $ENVIRONMENT"
            echo "Use: dev, prod ou test"
            exit 1
            ;;
    esac
}

# Deploy para desenvolvimento
deploy_development() {
    log "Deploy para desenvolvimento..."
    
    # Parar containers existentes
    docker-compose down || true
    
    # Subir ambiente completo
    docker-compose up -d
    
    # Aguardar inicialização
    log "Aguardando inicialização..."
    sleep 30
    
    # Verificar saúde da aplicação
    check_health
    
    log "✅ Deploy de desenvolvimento concluído"
    log "🌐 Aplicação: http://localhost:8082"
    log "📊 Grafana: http://localhost:3000 (admin/admin123)"
    log "📈 Prometheus: http://localhost:9090"
}

# Deploy para produção
deploy_production() {
    log "Deploy para produção..."
    
    # Backup da versão atual
    if docker ps | grep -q $APP_NAME; then
        log "Fazendo backup da versão atual..."
        docker tag $APP_NAME:latest $APP_NAME:backup-$(date +%Y%m%d)
    fi
    
    # Deploy com zero downtime
    docker-compose -f docker-compose.prod.yml up -d --no-deps pocsigmet-app
    
    # Verificar saúde
    check_health
    
    log "✅ Deploy de produção concluído"
}

# Deploy para testes
deploy_test() {
    log "Deploy para testes..."
    
    # Executar apenas a aplicação
    docker run -d \
        --name pocsigmet-test \
        -p 8083:8082 \
        -e SPRING_PROFILES_ACTIVE=test \
        $APP_NAME:$VERSION
    
    sleep 20
    check_health "8083"
    
    log "✅ Deploy de teste concluído"
    log "🧪 Aplicação de teste: http://localhost:8083"
}

# Verificar saúde da aplicação
check_health() {
    local port=${1:-8082}
    local max_attempts=10
    local attempt=1
    
    log "Verificando saúde da aplicação (porta $port)..."
    
    while [ $attempt -le $max_attempts ]; do
        if curl -f http://localhost:$port/actuator/health &> /dev/null; then
            log "✅ Aplicação está saudável"
            return 0
        fi
        
        log "Tentativa $attempt/$max_attempts - Aguardando..."
        sleep 10
        ((attempt++))
    done
    
    log "❌ Aplicação não respondeu após $max_attempts tentativas"
    exit 1
}

# Função de limpeza
cleanup() {
    log "Limpando recursos temporários..."
    docker system prune -f
    log "✅ Limpeza concluída"
}

# Menu de ajuda
show_help() {
    echo "POC SIGMET - Build & Deploy Script"
    echo ""
    echo "Uso: $0 [AMBIENTE] [OPÇÕES]"
    echo ""
    echo "Ambientes:"
    echo "  dev     - Desenvolvimento (padrão)"
    echo "  prod    - Produção"
    echo "  test    - Testes"
    echo ""
    echo "Opções:"
    echo "  --help  - Mostra esta ajuda"
    echo "  --clean - Apenas limpeza"
    echo ""
    echo "Exemplos:"
    echo "  $0 dev"
    echo "  $0 prod"
    echo "  $0 --clean"
}

# Processamento de argumentos
case $1 in
    "--help")
        show_help
        exit 0
        ;;
    "--clean")
        cleanup
        exit 0
        ;;
esac

# Execução principal
main() {
    log "Iniciando processo de build e deploy..."
    
    check_dependencies
    build_application
    build_docker_image
    deploy
    
    log "🎉 Processo concluído com sucesso!"
}

# Trap para limpeza em caso de erro
trap 'log "❌ Erro detectado. Abortando..."; exit 1' ERR

# Executar função principal
main
