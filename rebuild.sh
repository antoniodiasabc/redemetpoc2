#!/bin/bash
set -e

echo "🧪 Testes unitários..."
mvn test -Dtest="RedisMetarCacheServiceTest,NeighborSigmetParserTest,SigwxServiceTest,WindBarbServiceTest,SimpleBaselineTest" | grep -E "Tests run|FAIL|BUILD"

echo "🔨 Build Maven..."
mvn package -DskipTests -q

echo "🐳 Rebuild e deploy sem downtime..."
docker compose build app
docker compose up -d --no-deps --scale app=2 app

echo "⏳ Aguardando health checks..."
until curl -sf http://localhost:80/health > /dev/null 2>&1; do
    echo "  aguardando app..."
    sleep 5
done
echo "  ✅ App respondendo"

docker compose ps

echo "🧪 Testes de integração..."
mvn test -Dtest="EndpointContractTest,EndpointIntegrationTest,ConcurrencyTest" -Dtest.base.url="http://localhost:80" | grep -E "Tests run|FAIL|BUILD"

echo "✅ Concluído!"
