#!/bin/bash

# Teste rápido com 500 usuários por 30 segundos
CONCURRENT_USERS=500
BASE_URL="http://localhost"
DURATION=30

echo "🧪 Teste rápido: $CONCURRENT_USERS usuários por $DURATION segundos"

# Função simplificada
test_endpoint() {
    local endpoint=$1
    local requests=$2
    
    for i in $(seq 1 $requests); do
        curl -s -w "%{http_code}," "$BASE_URL$endpoint" > /dev/null &
    done
}

# Testa diferentes endpoints simultaneamente
echo "📡 Testando endpoints principais..."

test_endpoint "/" 100 &
test_endpoint "/metar_top20_sb" 150 &
test_endpoint "/metar_top50_sb" 100 &
test_endpoint "/health" 50 &
test_endpoint "/data/canal16_latest.jpg" 50 &
test_endpoint "/data/convection_hsv_severa_nooverlap.png" 50 &

echo "⏱️  Aguardando conclusão..."
wait

echo "✅ Teste rápido concluído!"
echo "📊 Status dos containers:"
docker ps --format "table {{.Names}}\t{{.Status}}"
