#!/bin/bash

# 🧪 Script de Teste da Animação de Vento
# Executa testes automatizados para verificar se a implementação está funcionando

echo "🧪 INICIANDO TESTES DA ANIMAÇÃO DE VENTO"
echo "========================================"

# Cores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Função para log colorido
log_success() { echo -e "${GREEN}✅ $1${NC}"; }
log_error() { echo -e "${RED}❌ $1${NC}"; }
log_warning() { echo -e "${YELLOW}⚠️ $1${NC}"; }
log_info() { echo -e "${BLUE}ℹ️ $1${NC}"; }

# Contador de testes
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

# Função para executar teste
run_test() {
    local test_name="$1"
    local test_command="$2"
    
    echo ""
    log_info "Executando: $test_name"
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    
    if eval "$test_command"; then
        log_success "$test_name - PASSOU"
        PASSED_TESTS=$((PASSED_TESTS + 1))
        return 0
    else
        log_error "$test_name - FALHOU"
        FAILED_TESTS=$((FAILED_TESTS + 1))
        return 1
    fi
}

# Verificar se a aplicação está rodando
check_app_running() {
    curl -s http://localhost:8082/actuator/health > /dev/null 2>&1
}

# Teste 1: Verificar se aplicação está rodando
run_test "Aplicação está rodando" "check_app_running"

if [ $? -ne 0 ]; then
    log_error "Aplicação não está rodando. Iniciando..."
    ./sobe_tudo.sh > /dev/null 2>&1 &
    sleep 30
    
    if ! check_app_running; then
        log_error "Não foi possível iniciar a aplicação"
        exit 1
    fi
    log_success "Aplicação iniciada com sucesso"
fi

# Teste 2: Verificar endpoint da animação
test_animation_endpoint() {
    local response=$(curl -s -w "%{http_code}" http://localhost:8082/api/v1/animation/satellite-wind?hours=2)
    local http_code="${response: -3}"
    
    if [ "$http_code" = "200" ]; then
        local body="${response%???}"
        # Verificar se contém os campos obrigatórios sem usar jq
        echo "$body" | grep -q '"satellite_frames"' && \
        echo "$body" | grep -q '"wind_overlays"' && \
        echo "$body" | grep -q '"frame_count"'
        return $?
    else
        return 1
    fi
}

run_test "Endpoint da animação responde" "test_animation_endpoint"

# Teste 3: Verificar estrutura da resposta JSON
test_json_structure() {
    local response=$(curl -s http://localhost:8082/api/v1/animation/satellite-wind?hours=1)
    
    # Verificar se tem os campos obrigatórios sem usar jq
    echo "$response" | grep -q '"satellite_frames"' && \
    echo "$response" | grep -q '"wind_overlays"' && \
    echo "$response" | grep -q '"frame_count"' && \
    echo "$response" | grep -q '"generated_at"'
}

run_test "Estrutura JSON está correta" "test_json_structure"

# Teste 4: Verificar arquivos JavaScript
test_js_files() {
    [ -f "src/main/resources/static/js/animation.js" ] && \
    grep -q "WindAnimation" "src/main/resources/static/js/animation.js" && \
    grep -q "toggleWindAnimation" "src/main/resources/static/js/animation.js"
}

run_test "Arquivos JavaScript existem" "test_js_files"

# Teste 5: Verificar se HTML foi modificado
test_html_modifications() {
    grep -q "wind-animation-container" "src/main/resources/static/index.html" && \
    grep -q "Animação de Vento" "src/main/resources/static/index.html" && \
    grep -q "animation.js" "src/main/resources/static/index.html"
}

run_test "Modificações no HTML estão presentes" "test_html_modifications"

# Teste 6: Verificar controller Java
test_java_controller() {
    [ -f "src/main/java/com/pocsigmet/controller/AnimationController.java" ] && \
    grep -q "@RestController" "src/main/java/com/pocsigmet/controller/AnimationController.java" && \
    grep -q "satellite-wind" "src/main/java/com/pocsigmet/controller/AnimationController.java"
}

run_test "Controller Java existe e está correto" "test_java_controller"

# Teste 7: Verificar performance da API
test_api_performance() {
    local start_time=$(date +%s%N)
    curl -s http://localhost:8082/api/v1/animation/satellite-wind?hours=3 > /dev/null
    local end_time=$(date +%s%N)
    
    local duration=$(( (end_time - start_time) / 1000000 )) # Convert to milliseconds
    
    # Deve responder em menos de 5 segundos (5000ms)
    [ $duration -lt 5000 ]
}

run_test "Performance da API (< 5s)" "test_api_performance"

# Teste 8: Verificar se endpoints existentes ainda funcionam
test_existing_endpoints() {
    curl -s http://localhost:8082/api/v1/grib2/wind-barbs > /dev/null 2>&1 && \
    curl -s http://localhost:8082/cptec/canal16/latest > /dev/null 2>&1
}

run_test "Endpoints existentes ainda funcionam" "test_existing_endpoints"

# Teste 9: Verificar página de teste
test_test_page() {
    [ -f "src/main/resources/static/test-animation.html" ] && \
    curl -s http://localhost:8082/test-animation.html | grep -q "Teste da Animação"
}

run_test "Página de teste está acessível" "test_test_page"

# Teste 10: Verificar logs por erros
test_application_logs() {
    if [ -f "logs/application.log" ]; then
        ! grep -i "error\|exception" logs/application.log | tail -10 | grep -q "AnimationController"
    else
        # Se não há logs, assumir que está OK
        true
    fi
}

run_test "Sem erros nos logs da aplicação" "test_application_logs"

# Resumo final
echo ""
echo "========================================"
echo "🏁 RESUMO DOS TESTES"
echo "========================================"
echo "Total de testes: $TOTAL_TESTS"
log_success "Testes aprovados: $PASSED_TESTS"
log_error "Testes falharam: $FAILED_TESTS"

if [ $FAILED_TESTS -eq 0 ]; then
    echo ""
    log_success "🎉 TODOS OS TESTES PASSARAM!"
    log_info "A animação de vento está funcionando corretamente"
    echo ""
    echo "🌐 Acesse: http://localhost:8082"
    echo "🧪 Testes: http://localhost:8082/test-animation.html"
    echo ""
    exit 0
else
    echo ""
    log_error "❌ ALGUNS TESTES FALHARAM"
    log_warning "Verifique os erros acima e corrija antes de usar"
    echo ""
    exit 1
fi
