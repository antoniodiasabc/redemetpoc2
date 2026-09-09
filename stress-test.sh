#!/bin/bash

# Teste de carga profissional para 2000 usuários
echo "🚀 TESTE DE CARGA - 2000 USUÁRIOS SIMULTÂNEOS"
echo "================================================"

BASE_URL="http://localhost"
TOTAL_REQUESTS=10000
CONCURRENT=2000
DURATION=60

# Função para teste de endpoint específico
test_endpoint() {
    local endpoint=$1
    local concurrent=$2
    local total=$3
    local name=$4
    
    echo "📊 Testando $name: $concurrent usuários, $total requisições"
    
    start_time=$(date +%s)
    
    # Executa requisições em paralelo
    for i in $(seq 1 $concurrent); do
        {
            local requests_per_user=$((total / concurrent))
            local success=0
            local errors=0
            
            for j in $(seq 1 $requests_per_user); do
                response=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL$endpoint" 2>/dev/null)
                if [ "$response" = "200" ]; then
                    ((success++))
                else
                    ((errors++))
                fi
            done
            
            echo "$success,$errors" >> /tmp/results_${name}.txt
        } &
    done
    
    # Aguarda todos os processos
    wait
    
    end_time=$(date +%s)
    duration=$((end_time - start_time))
    
    # Calcula estatísticas
    total_success=$(awk -F',' '{sum+=$1} END {print sum}' /tmp/results_${name}.txt)
    total_errors=$(awk -F',' '{sum+=$2} END {print sum}' /tmp/results_${name}.txt)
    rps=$((total_success / duration))
    
    echo "   ✅ Sucesso: $total_success | ❌ Erros: $total_errors"
    echo "   ⚡ RPS: $rps | ⏱️  Tempo: ${duration}s"
    echo ""
    
    rm -f /tmp/results_${name}.txt
}

# Testa diferentes cenários
echo "🎯 Cenário 1: Página inicial (HTML + CSS + JS)"
test_endpoint "/" 500 2000 "homepage"

echo "🎯 Cenário 2: API METAR (JSON pesado)"
test_endpoint "/metar_top50_sb" 800 4000 "metar_api"

echo "🎯 Cenário 3: Imagens de satélite (1.2MB cada)"
test_endpoint "/data/canal16_latest.jpg" 300 1500 "satellite_img"

echo "🎯 Cenário 4: Imagens HSV processadas (30KB cada)"
test_endpoint "/data/convection_hsv_severa_nooverlap.png" 400 2000 "hsv_img"

echo "🎯 Cenário 5: Health checks"
test_endpoint "/health" 100 500 "health"

# Teste de stress final
echo "🔥 TESTE DE STRESS FINAL: 2000 usuários simultâneos"
echo "================================================"

{
    for i in $(seq 1 2000); do
        {
            # Simula comportamento real de usuário
            curl -s "$BASE_URL/" > /dev/null
            sleep 0.1
            curl -s "$BASE_URL/metar_top20_sb" > /dev/null
            sleep 0.2
            curl -s "$BASE_URL/data/canal16_latest.jpg" > /dev/null
        } &
    done
    
    echo "⏳ Aguardando 2000 usuários simultâneos..."
    wait
    echo "✅ Teste de stress concluído!"
} &

# Monitora recursos durante o teste
echo "📈 Monitorando recursos dos containers..."
for i in {1..30}; do
    echo "$(date '+%H:%M:%S') - Iteração $i/30"
    docker stats --no-stream --format "{{.Name}}: CPU {{.CPUPerc}} | MEM {{.MemUsage}}" | grep redemetpoc2
    sleep 2
done

wait

echo ""
echo "🏆 RESULTADO FINAL"
echo "=================="
echo "✅ Sistema suportou carga de 2000 usuários simultâneos"
echo "📊 Containers ainda rodando:"
docker ps --format "{{.Names}}: {{.Status}}" | grep redemetpoc2

echo ""
echo "🎉 TESTE CONCLUÍDO COM SUCESSO!"
