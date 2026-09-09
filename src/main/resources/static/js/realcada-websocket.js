// WebSocket para atualizações automáticas de imagens realçada
class RealcadaWebSocketClient {
    constructor() {
        this.ws = null;
        this.reconnectInterval = 5000; // 5 segundos
        this.maxReconnectAttempts = 10;
        this.reconnectAttempts = 0;
        this.isConnected = false;
    }

    connect() {
        try {
            // Usar WebSocket nativo do navegador
            const wsUrl = `ws://${window.location.host}/ws/realcada`;
            console.log('🔌 Conectando WebSocket:', wsUrl);
            
            // Para demonstração, vamos simular com EventSource (Server-Sent Events)
            // que é mais simples de implementar no servidor HTTP atual
            this.connectWithEventSource();
            
        } catch (error) {
            console.error('❌ Erro ao conectar WebSocket:', error);
            this.scheduleReconnect();
        }
    }

    connectWithEventSource() {
        // Simular WebSocket com polling para demonstração
        console.log('🔌 Iniciando monitoramento de novas imagens...');
        this.isConnected = true;
        
        // Verificar novas imagens a cada 10 segundos
        this.pollInterval = setInterval(() => {
            this.checkForNewImages();
        }, 10000);
        
        console.log('✅ Monitoramento ativo - verificando a cada 10s');
    }

    async checkForNewImages() {
        try {
            // Buscar lista atual de imagens
            const response = await fetch('/api/v1/animation/realcada-images?count=1');
            const data = await response.json();
            
            if (data.images && data.images.length > 0) {
                const latestImage = data.images[data.images.length - 1]; // Mais recente
                
                // Verificar se é uma nova imagem
                if (!this.lastImageFilename || this.lastImageFilename !== latestImage.filename) {
                    if (this.lastImageFilename) { // Não notificar na primeira vez
                        console.log('🆕 Nova imagem detectada:', latestImage.filename);
                        this.onNewImage(latestImage);
                    }
                    this.lastImageFilename = latestImage.filename;
                }
            }
            
        } catch (error) {
            console.error('❌ Erro ao verificar novas imagens:', error);
        }
    }

    onNewImage(imageData) {
        console.log('📥 Processando nova imagem:', imageData.filename);
        
        // Notificar a animação realçada se estiver ativa
        if (typeof realcadaAnimation !== 'undefined' && realcadaAnimation.frames.length > 0) {
            realcadaAnimation.addNewFrame(imageData);
        }
        
        // Mostrar notificação visual
        this.showNotification(imageData);
    }

    showNotification(imageData) {
        // Criar notificação visual temporária
        const notification = document.createElement('div');
        notification.style.cssText = `
            position: fixed;
            top: 20px;
            right: 20px;
            background: #FF9800;
            color: white;
            padding: 15px;
            border-radius: 8px;
            box-shadow: 0 4px 8px rgba(0,0,0,0.3);
            z-index: 10000;
            font-family: Arial, sans-serif;
            max-width: 300px;
        `;
        
        const timestamp = new Date(imageData.timestamp).toLocaleTimeString();
        notification.innerHTML = `
            <strong>🆕 Nova Imagem Realçada</strong><br>
            <small>${imageData.filename}</small><br>
            <small>Horário: ${timestamp}</small>
        `;
        
        document.body.appendChild(notification);
        
        // Remover após 5 segundos
        setTimeout(() => {
            if (notification.parentNode) {
                notification.parentNode.removeChild(notification);
            }
        }, 5000);
        
        console.log('🔔 Notificação exibida para:', imageData.filename);
    }

    disconnect() {
        this.isConnected = false;
        
        if (this.pollInterval) {
            clearInterval(this.pollInterval);
            this.pollInterval = null;
        }
        
        console.log('🔌 WebSocket desconectado');
    }

    scheduleReconnect() {
        if (this.reconnectAttempts < this.maxReconnectAttempts) {
            this.reconnectAttempts++;
            console.log(`🔄 Tentativa de reconexão ${this.reconnectAttempts}/${this.maxReconnectAttempts} em ${this.reconnectInterval/1000}s`);
            
            setTimeout(() => {
                this.connect();
            }, this.reconnectInterval);
        } else {
            console.log('❌ Máximo de tentativas de reconexão atingido');
        }
    }
}

// Instância global
const realcadaWebSocket = new RealcadaWebSocketClient();

// Conectar automaticamente quando a página carregar
document.addEventListener('DOMContentLoaded', () => {
    console.log('🚀 Iniciando WebSocket para imagens realçada...');
    realcadaWebSocket.connect();
});

// Desconectar quando a página for fechada
window.addEventListener('beforeunload', () => {
    realcadaWebSocket.disconnect();
});
