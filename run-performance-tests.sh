#!/bin/bash

echo "🚀 TESTE DE PERFORMANCE - WIND BARBS (MANUAL)"
echo "=============================================="

# Função para medir tempo de resposta
measure_endpoint() {
    local level=$1
    echo "📊 Testando endpoint: /api/wind/barbs/$level"
    
    start_time=$(date +%s%3N)
    
    # Fazer requisição HTTP
    response=$(curl -s -w "%{http_code}" "http://localhost:8082/api/wind/barbs/$level")
    http_code="${response: -3}"
    
    end_time=$(date +%s%3N)
    duration=$((end_time - start_time))
    
    if [ "$http_code" = "200" ]; then
        # Contar pontos de dados (aproximado)
        data_points=$(echo "$response" | grep -o '"lat":' | wc -l)
        echo "✅ $level: ${duration}ms (${data_points} pontos) - HTTP $http_code"
    else
        echo "❌ $level: ${duration}ms - HTTP $http_code (ERRO)"
    fi
}

echo ""
echo "🔧 BASELINE - Primeira execução (sem cache)"
echo "-------------------------------------------"

# Limpar caches
echo "🗑️ Limpando caches..."
rm -f data/wind_cache_*.json 2>/dev/null

# Testar endpoints
levels=("surface" "fl050" "fl100" "fl180" "fl300")

for level in "${levels[@]}"; do
    measure_endpoint "$level"
    sleep 1  # Pausa entre requisições
done

echo ""
echo "🚀 COM CACHE - Segunda execução"
echo "------------------------------"

for level in "${levels[@]}"; do
    measure_endpoint "$level"
    sleep 1
done

echo ""
echo "✅ TESTE DE PERFORMANCE CONCLUÍDO"
echo "================================="
echo "💡 Compare os tempos da primeira vs segunda execução"
