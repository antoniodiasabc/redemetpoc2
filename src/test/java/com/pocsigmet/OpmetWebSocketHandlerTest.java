package com.pocsigmet;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Testes unitários para OpmetWebSocketHandler
 */
public class OpmetWebSocketHandlerTest {
    
    private TestOpmetSession testSession;
    private AtomicInteger messageCount;
    private AtomicReference<String> lastMessage;
    
    @BeforeEach
    void setUp() {
        messageCount = new AtomicInteger(0);
        lastMessage = new AtomicReference<>();
        testSession = new TestOpmetSession();
    }
    
    @AfterEach
    void tearDown() {
        OpmetWebSocketHandler.removeSession(testSession);
        OpmetWebSocketHandler.resetTestingMode();
    }
    
    @Test
    @DisplayName("Deve adicionar e remover sessões corretamente")
    void testSessionManagement() {
        // Inicialmente sem sessões
        assertEquals(0, OpmetWebSocketHandler.getSessionCount());
        
        // Adicionar sessão
        OpmetWebSocketHandler.addSession(testSession);
        assertEquals(1, OpmetWebSocketHandler.getSessionCount());
        
        // Remover sessão
        OpmetWebSocketHandler.removeSession(testSession);
        assertEquals(0, OpmetWebSocketHandler.getSessionCount());
    }
    
    @Test
    @DisplayName("Deve enviar mensagem de status ao adicionar sessão")
    void testStatusMessageOnConnect() throws InterruptedException {
        OpmetWebSocketHandler.addSession(testSession);
        
        // Aguardar mensagem assíncrona
        Thread.sleep(100);
        
        assertTrue(messageCount.get() > 0, "Deve receber pelo menos uma mensagem");
        String message = lastMessage.get();
        assertNotNull(message, "Mensagem não deve ser null");
        assertTrue(message.contains("\"type\":\"status\""), "Deve ser mensagem de status");
    }
    
    @Test
    @DisplayName("Deve processar mensagens de dados corretamente")
    void testDataMessageProcessing() throws InterruptedException {
        OpmetWebSocketHandler.setTokenForTesting("test-token-123");
        OpmetWebSocketHandler.addSession(testSession);
        
        // Simular mensagem normal — broadcastMessage é síncrono, capturar imediatamente
        String testMessage = "METAR SBSP 231200Z 12005KT 9999 FEW020 25/18 Q1013";
        OpmetWebSocketHandler.simulateMessageForTesting(testMessage);
        String receivedMessage = lastMessage.get();
        
        assertNotNull(receivedMessage);
        assertTrue(receivedMessage.contains("\"type\":\"data\""));
        assertTrue(receivedMessage.contains(testMessage));
        assertTrue(receivedMessage.contains("\"highlight\":false"));
    }
    
    @Test
    @DisplayName("Deve destacar mensagens SB (SIGMET)")
    void testSigmetHighlighting() throws InterruptedException {
        OpmetWebSocketHandler.setTokenForTesting("test-token-123");
        OpmetWebSocketHandler.addSession(testSession);
        
        // Simular mensagem SIGMET (contém SB)
        String sigmetMessage = "SIGMET SBBS A1 VALID 231200/231600 SBBS- SBBS BRASILIA FIR SEV TURB";
        OpmetWebSocketHandler.simulateMessageForTesting(sigmetMessage);
        String receivedMessage = lastMessage.get();
        
        assertNotNull(receivedMessage);
        assertTrue(receivedMessage.contains("\"highlight\":true"), "Mensagem SB deve ser destacada");
    }
    
    @Test
    @DisplayName("Deve escapar caracteres especiais em JSON")
    void testJsonEscaping() throws InterruptedException {
        OpmetWebSocketHandler.setTokenForTesting("test-token-123");
        OpmetWebSocketHandler.addSession(testSession);
        
        // Mensagem com caracteres especiais
        String specialMessage = "Test \"quotes\" and \\backslash and \n newline";
        OpmetWebSocketHandler.simulateMessageForTesting(specialMessage);
        String receivedMessage = lastMessage.get();
        
        assertNotNull(receivedMessage);
        // Verificar se JSON é válido (não quebra)
        assertTrue(receivedMessage.contains("\\\"quotes\\\""));
        assertTrue(receivedMessage.contains("\\\\backslash"));
        assertTrue(receivedMessage.contains("\\n"));
    }
    
    @Test
    @DisplayName("Deve gerenciar múltiplas sessões")
    void testMultipleSessions() throws InterruptedException {
        TestOpmetSession session1 = new TestOpmetSession("session-1");
        TestOpmetSession session2 = new TestOpmetSession("session-2");
        
        OpmetWebSocketHandler.addSession(session1);
        OpmetWebSocketHandler.addSession(session2);
        
        assertEquals(2, OpmetWebSocketHandler.getSessionCount());
        
        // Simular mensagem
        OpmetWebSocketHandler.setTokenForTesting("test-token-123");
        OpmetWebSocketHandler.simulateMessageForTesting("Test broadcast message");
        
        // Ambas sessões devem receber
        assertTrue(session1.getMessageCount() > 0);
        assertTrue(session2.getMessageCount() > 0);
        
        // Cleanup
        OpmetWebSocketHandler.removeSession(session1);
        OpmetWebSocketHandler.removeSession(session2);
    }
    
    @Test
    @DisplayName("Deve remover sessões inválidas automaticamente")
    void testInvalidSessionRemoval() throws InterruptedException {
        TestOpmetSession validSession = new TestOpmetSession("valid");
        TestOpmetSession invalidSession = new TestOpmetSession("invalid", true); // Simula erro
        
        OpmetWebSocketHandler.addSession(validSession);
        OpmetWebSocketHandler.addSession(invalidSession);
        
        assertEquals(2, OpmetWebSocketHandler.getSessionCount());
        
        // Simular mensagem - sessão inválida deve ser removida
        OpmetWebSocketHandler.setTokenForTesting("test-token-123");
        OpmetWebSocketHandler.simulateMessageForTesting("Test message");
        
        // Sessão inválida deve ter sido removida
        assertEquals(1, OpmetWebSocketHandler.getSessionCount());
        
        // Cleanup
        OpmetWebSocketHandler.removeSession(validSession);
    }
    
    @Test
    @DisplayName("Deve criar mensagens de status corretamente")
    void testStatusMessageFormat() throws InterruptedException {
        OpmetWebSocketHandler.addSession(testSession);
        
        Thread.sleep(100);
        
        String statusMessage = lastMessage.get();
        assertNotNull(statusMessage);
        
        // Verificar formato JSON da mensagem de status
        assertTrue(statusMessage.contains("\"type\":\"status\""));
        assertTrue(statusMessage.contains("\"connected\":"));
        assertTrue(statusMessage.contains("\"timestamp\":"));
        assertTrue(statusMessage.contains("\"sessions\":"));
    }
    
    // Classe de teste para simular sessão WebSocket
    private class TestOpmetSession implements OpmetWebSocketHandler.OpmetSession {
        private final String sessionId;
        private final boolean simulateError;
        private final AtomicInteger localMessageCount = new AtomicInteger(0);
        
        public TestOpmetSession() {
            this("test-session-" + System.currentTimeMillis(), false);
        }
        
        public TestOpmetSession(String sessionId) {
            this(sessionId, false);
        }
        
        public TestOpmetSession(String sessionId, boolean simulateError) {
            this.sessionId = sessionId;
            this.simulateError = simulateError;
        }
        
        @Override
        public void sendMessage(String message) throws Exception {
            if (simulateError) {
                throw new RuntimeException("Simulated session error");
            }
            
            messageCount.incrementAndGet();
            localMessageCount.incrementAndGet();
            lastMessage.set(message);
            
            System.out.println("📨 Sessão " + sessionId + " recebeu: " + 
                             (message.length() > 100 ? message.substring(0, 100) + "..." : message));
        }
        
        @Override
        public String getSessionId() {
            return sessionId;
        }
        
        public int getMessageCount() {
            return localMessageCount.get();
        }
    }
}
