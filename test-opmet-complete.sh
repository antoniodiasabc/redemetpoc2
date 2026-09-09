#!/bin/bash

echo "🧪 Teste Completo OPMET - Mensagens Reais no Frontend"
echo "=================================================="

# 1. Testar se backend está rodando
echo "1️⃣ Testando se backend está ativo..."
if curl -s "http://localhost:8082" > /dev/null; then
    echo "✅ Backend rodando"
else
    echo "❌ Backend não está rodando!"
    exit 1
fi

# 2. Iniciar sessão WebSocket OPMET
echo ""
echo "2️⃣ Iniciando sessão WebSocket OPMET..."
RESPONSE=$(curl -s "http://localhost:8082/ws/opmet")
echo "Resposta: $RESPONSE"

if echo "$RESPONSE" | grep -q "connected"; then
    echo "✅ Sessão WebSocket criada"
    SESSION_ID=$(echo "$RESPONSE" | grep -o '"sessionId":"[^"]*"' | cut -d'"' -f4)
    echo "📋 Session ID: $SESSION_ID"
else
    echo "❌ Falha ao criar sessão WebSocket"
    exit 1
fi

# 3. Aguardar conexão OPMET real
echo ""
echo "3️⃣ Aguardando conexão OPMET real (10 segundos)..."
sleep 10

# 4. Verificar status da conexão
echo ""
echo "4️⃣ Verificando status da conexão OPMET..."
STATUS=$(curl -s "http://localhost:8082/api/v1/opmet/messages")
echo "Status: $STATUS"

CONNECTED=$(echo "$STATUS" | grep -o '"connected":[^,]*' | cut -d':' -f2)
SESSIONS=$(echo "$STATUS" | grep -o '"sessions":[^,]*' | cut -d':' -f2)

if [ "$CONNECTED" = "true" ]; then
    echo "✅ OPMET conectado com $SESSIONS sessões"
else
    echo "❌ OPMET não conectado"
fi

# 5. Simular mensagem de teste
echo ""
echo "5️⃣ Simulando mensagem de teste..."
# Usar OpmetWebSocketHandler para simular
java -cp . -c "
import com.pocsigmet.OpmetWebSocketHandler;
public class TestMessage {
    public static void main(String[] args) {
        OpmetWebSocketHandler.simulateMessageForTesting(\"SPECI SBSP 241500Z 12005KT 9999 FEW020 25/18 Q1013 NOSIG=\");
        System.out.println(\"Mensagem de teste enviada\");
    }
}
" 2>/dev/null || echo "⚠️ Simulação via Java não disponível"

# 6. Aguardar processamento
echo ""
echo "6️⃣ Aguardando processamento (5 segundos)..."
sleep 5

# 7. Verificar se mensagens estão sendo capturadas
echo ""
echo "7️⃣ Verificando mensagens capturadas..."
MESSAGES=$(curl -s "http://localhost:8082/api/v1/opmet/messages")
echo "Mensagens: $MESSAGES"

MESSAGE_COUNT=$(echo "$MESSAGES" | grep -o '"messageCount":[^,]*' | cut -d':' -f2)
RECENT_MESSAGES=$(echo "$MESSAGES" | grep -o '"recentMessages":\[[^\]]*\]')

echo "📊 Total de mensagens: $MESSAGE_COUNT"
echo "📝 Mensagens recentes: $RECENT_MESSAGES"

# 8. Teste de conectividade frontend
echo ""
echo "8️⃣ Testando conectividade do frontend..."

# Criar teste JavaScript temporário
cat > /tmp/opmet_test.js << 'EOF'
// Teste automatizado do frontend OPMET
async function testOpmetFrontend() {
    console.log('🧪 Iniciando teste frontend OPMET...');
    
    try {
        // Simular conexão
        const connectResponse = await fetch('/ws/opmet');
        const connectData = await connectResponse.json();
        console.log('✅ Conexão:', connectData);
        
        // Aguardar 2 segundos
        await new Promise(resolve => setTimeout(resolve, 2000));
        
        // Buscar mensagens
        const messagesResponse = await fetch('/api/v1/opmet/messages');
        const messagesData = await messagesResponse.json();
        console.log('📨 Mensagens:', messagesData);
        
        if (messagesData.connected) {
            console.log('✅ Frontend pode acessar mensagens OPMET');
            console.log('📊 Sessões:', messagesData.sessions);
            console.log('📝 Total mensagens:', messagesData.messageCount || 0);
            
            if (messagesData.recentMessages && messagesData.recentMessages.length > 0) {
                console.log('✅ Mensagens recentes encontradas:');
                messagesData.recentMessages.forEach((msg, i) => {
                    console.log(`   ${i+1}. ${msg}`);
                });
            } else {
                console.log('⚠️ Nenhuma mensagem recente (normal se não há atividade OPMET)');
            }
        } else {
            console.log('❌ OPMET não conectado');
        }
        
    } catch (error) {
        console.error('❌ Erro no teste:', error);
    }
}

testOpmetFrontend();
EOF

echo "📄 Teste JavaScript criado em /tmp/opmet_test.js"

# 9. Verificar se OpmetSwingClient está recebendo mensagens
echo ""
echo "9️⃣ Verificando OpmetSwingClient..."
SWING_PROCESS=$(ps aux | grep -v grep | grep OpmetSwingClient | wc -l)
if [ "$SWING_PROCESS" -gt 0 ]; then
    echo "✅ OpmetSwingClient está rodando"
    echo "📋 Processos: $SWING_PROCESS"
else
    echo "⚠️ OpmetSwingClient não está rodando"
fi

# 10. Resumo final
echo ""
echo "🎯 RESUMO DO TESTE"
echo "=================="
echo "Backend: ✅ Rodando"
echo "WebSocket: ✅ Sessão criada"
echo "OPMET: $([ "$CONNECTED" = "true" ] && echo "✅ Conectado" || echo "❌ Desconectado")"
echo "Sessões: $SESSIONS"
echo "Mensagens: $MESSAGE_COUNT"
echo "SwingClient: $([ "$SWING_PROCESS" -gt 0 ] && echo "✅ Ativo" || echo "⚠️ Inativo")"

echo ""
echo "📋 PRÓXIMOS PASSOS:"
echo "1. Abra http://localhost:8082"
echo "2. Clique '📡 OPMET Live'"
echo "3. Clique 'Conectar'"
echo "4. Aguarde mensagens reais do DECEA"
echo ""
echo "🔍 Para debug detalhado:"
echo "   curl -s 'http://localhost:8082/api/v1/opmet/messages' | python3 -m json.tool"
echo ""
echo "✅ Teste concluído!"
