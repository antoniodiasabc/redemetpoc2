#!/bin/bash

# Teste de carga para 2000 usuários simultâneos
# Simula diferentes endpoints da aplicação

CONCURRENT_USERS=2000
BASE_URL="http://localhost"
DURATION=60  # segundos

echo "🚀 Iniciando teste de carga: $CONCURRENT_USERS usuários por $DURATION segundos"

# Função para simular um usuário
simulate_user() {
    local user_id=$1
    local start_time=$(date +%s)
    local end_time=$((start_time + DURATION))
    
    while [ $(date +%s) -lt $end_time ]; do
        # Simula navegação típica de usuário
        case $((RANDOM % 6)) in
            0) curl -s "$BASE_URL/" > /dev/null ;;
            1) curl -s "$BASE_URL/metar_top20_sb" > /dev/null ;;
            2) curl -s "$BASE_URL/metar_top50_sb" > /dev/null ;;
            3) curl -s "$BASE_URL/health" > /dev/null ;;
            4) curl -s "$BASE_URL/data/canal16_latest.jpg" > /dev/null ;;
            5) curl -s "$BASE_URL/data/convection_hsv_severa_nooverlap.png" > /dev/null ;;
        esac
        
        # Pausa aleatória entre 1-5 segundos (comportamento real)
        sleep $((RANDOM % 5 + 1))
    done
}

# Inicia usuários em background
echo "📊 Iniciando $CONCURRENT_USERS processos simultâneos..."
for i in $(seq 1 $CONCURRENT_USERS); do
    simulate_user $i &
    
    # Mostra progresso a cada 100 usuários
    if [ $((i % 100)) -eq 0 ]; then
        echo "   ✓ $i usuários iniciados..."
    fi
done

echo "⏱️  Teste em execução por $DURATION segundos..."
echo "📈 Monitorando containers..."

# Monitora recursos durante o teste
monitor_resources() {
    while [ $(jobs -r | wc -l) -gt 0 ]; do
        echo "$(date '+%H:%M:%S') - Processos ativos: $(jobs -r | wc -l)"
        docker stats --no-stream --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}" | grep redemetpoc2
        sleep 10
    done
}

monitor_resources &

# Aguarda todos os processos terminarem
wait

echo "✅ Teste de carga concluído!"
echo "📊 Verificando logs dos containers..."

# Mostra estatísticas finais
docker logs redemetpoc2_bk07042026_nginx_1 2>&1 | tail -20
echo "🎯 Teste finalizado com sucesso!"
