/**
 * Cliente OPMET Live - Integração com WebSocket OPMET
 * Replica funcionalidade do OpmetSwingClient para web
 */
class OpmetLiveClient {
    constructor() {
        this.isConnected = false;
        this.messageCount = 0;
        this.modal = null;
        this.messageContainer = null;
        this.statusElement = null;
        this.connectButton = null;
        this.disconnectButton = null;
        this.clearButton = null;
        this.messageCountElement = null;
        this.autoScroll = true;
        this.maxMessages = 1000;
        this.processedMessages = new Set();
        this.lastNewMessageTime = Date.now();
    }

    init() {
        this.createModal();
        this.createButton();
        console.log('🚀 OPMET Live Client inicializado');
    }

    createButton() {
        // Adicionar botão OPMET Live na interface principal
        const controlsContainer = document.querySelector('.controls') || document.body;
        
        const opmetButton = document.createElement('button');
        opmetButton.innerHTML = '📡 OPMET Live';
        opmetButton.style.cssText = `
            background: #2196F3;
            color: white;
            border: none;
            padding: 8px 15px;
            border-radius: 4px;
            cursor: pointer;
            margin: 5px;
            font-size: 14px;
        `;
        
        opmetButton.onclick = () => this.showModal();
        controlsContainer.appendChild(opmetButton);
    }

    createModal() {
        // Criar modal popup lateral direito
        this.modal = document.createElement('div');
        this.modal.style.cssText = `
            display: none;
            position: fixed;
            z-index: 10000;
            top: 10px;
            right: 10px;
            width: 400px;
            height: calc(100vh - 20px);
            background-color: rgba(0,0,0,0.95);
            border: 2px solid #00ff00;
            border-radius: 8px;
            box-shadow: 0 4px 20px rgba(0, 255, 0, 0.3);
        `;

        const modalContent = document.createElement('div');
        modalContent.style.cssText = `
            background-color: #1a1a1a;
            width: 100%;
            height: 100%;
            display: flex;
            flex-direction: column;
            border-radius: 8px;
            color: #00ff00;
        `;

        // Header do modal
        const header = document.createElement('div');
        header.style.cssText = `
            background: #333;
            color: #00ff00;
            padding: 10px 15px;
            border-radius: 8px 8px 0 0;
            display: flex;
            justify-content: space-between;
            align-items: center;
            border-bottom: 1px solid #00ff00;
        `;

        const title = document.createElement('h3');
        title.textContent = '📡 OPMET Live - Mensagens em Tempo Real';
        title.style.margin = '0';

        const closeButton = document.createElement('span');
        closeButton.innerHTML = '&times;';
        closeButton.style.cssText = `
            font-size: 28px;
            font-weight: bold;
            cursor: pointer;
            line-height: 1;
        `;
        closeButton.onclick = () => this.hideModal();

        header.appendChild(title);
        header.appendChild(closeButton);

        // Controles
        const controls = document.createElement('div');
        controls.style.cssText = `
            padding: 10px 15px;
            background: #2a2a2a;
            border-bottom: 1px solid #444;
            display: flex;
            gap: 8px;
            align-items: center;
            flex-wrap: wrap;
            font-size: 12px;
        `;

        this.statusElement = document.createElement('span');
        this.statusElement.textContent = '🔴 Desconectado';
        this.statusElement.style.cssText = `
            font-weight: bold;
            color: #f44336;
        `;

        this.connectButton = document.createElement('button');
        this.connectButton.textContent = 'Conectar';
        this.connectButton.style.cssText = `
            background: #4CAF50;
            color: white;
            border: none;
            padding: 8px 15px;
            border-radius: 4px;
            cursor: pointer;
        `;
        this.connectButton.onclick = () => this.connect();

        this.disconnectButton = document.createElement('button');
        this.disconnectButton.textContent = 'Desconectar';
        this.disconnectButton.style.cssText = `
            background: #f44336;
            color: white;
            border: none;
            padding: 8px 15px;
            border-radius: 4px;
            cursor: pointer;
        `;
        this.disconnectButton.disabled = true;
        this.disconnectButton.onclick = () => this.disconnect();

        this.clearButton = document.createElement('button');
        this.clearButton.textContent = 'Limpar';
        this.clearButton.style.cssText = `
            background: #9E9E9E;
            color: white;
            border: none;
            padding: 8px 15px;
            border-radius: 4px;
            cursor: pointer;
        `;
        this.clearButton.onclick = () => this.clearMessages();

        this.messageCountElement = document.createElement('span');
        this.messageCountElement.textContent = 'Mensagens: 0';
        this.messageCountElement.style.cssText = `
            margin-left: auto;
            font-size: 12px;
            color: #666;
        `;

        const autoScrollLabel = document.createElement('label');
        autoScrollLabel.style.cssText = `
            display: flex;
            align-items: center;
            gap: 5px;
            font-size: 12px;
            color: #666;
        `;

        const autoScrollCheckbox = document.createElement('input');
        autoScrollCheckbox.type = 'checkbox';
        autoScrollCheckbox.checked = this.autoScroll;
        autoScrollCheckbox.onchange = (e) => {
            this.autoScroll = e.target.checked;
        };

        autoScrollLabel.appendChild(autoScrollCheckbox);
        autoScrollLabel.appendChild(document.createTextNode('Auto-scroll'));

        // Checkbox IWXXM
        const iwxxmLabel = document.createElement('label');
        iwxxmLabel.style.cssText = `
            display: flex;
            align-items: center;
            gap: 5px;
            font-size: 12px;
            color: #666;
            margin-left: 10px;
        `;

        this.iwxxmCheckbox = document.createElement('input');
        this.iwxxmCheckbox.type = 'checkbox';
        this.iwxxmCheckbox.checked = false; // Default: sem parâmetro

        iwxxmLabel.appendChild(this.iwxxmCheckbox);
        iwxxmLabel.appendChild(document.createTextNode('IWXXM'));

        controls.appendChild(this.statusElement);
        controls.appendChild(this.connectButton);
        controls.appendChild(this.disconnectButton);
        controls.appendChild(this.clearButton);
        controls.appendChild(autoScrollLabel);
        controls.appendChild(iwxxmLabel);
        controls.appendChild(this.messageCountElement);

        // Container de mensagens com scroll
        this.messageContainer = document.createElement('div');
        this.messageContainer.style.cssText = `
            flex: 1;
            overflow-y: auto;
            padding: 10px 15px;
            background: #1a1a1a;
            font-family: 'Courier New', monospace;
            font-size: 11px;
            line-height: 1.3;
            color: #00ff00;
            border-radius: 0 0 8px 8px;
        `;

        // Mensagem inicial
        this.addMessage('Sistema OPMET Live inicializado. Clique em "Conectar" para começar.', 'info');

        modalContent.appendChild(header);
        modalContent.appendChild(controls);
        modalContent.appendChild(this.messageContainer);
        this.modal.appendChild(modalContent);
        document.body.appendChild(this.modal);
    }

    showModal() {
        this.modal.style.display = 'block';
        console.log('📱 Modal OPMET Live aberto');
    }

    hideModal() {
        this.modal.style.display = 'none';
        console.log('📱 Modal OPMET Live fechado');
    }

    async connect() {
        if (this.isConnected) return;

        this.addMessage('🔌 Conectando ao OPMET...', 'info');
        this.connectButton.disabled = true;
        this.updateStatus('🟡 Conectando...', '#FF9800');

        try {
            // Verificar se IWXXM está marcado
            const useIwxxm = this.iwxxmCheckbox.checked;
            const iwxxmParam = useIwxxm ? '?iwxxm=true' : '';
            
            this.addMessage(`🔧 Parâmetros: ${useIwxxm ? 'iwxxm=true' : 'sem parâmetros'}`, 'info');

            // Iniciar conexão WebSocket via endpoint
            const response = await fetch(`/ws/opmet${iwxxmParam}`);
            const data = await response.json();

            if (data.status === 'connected') {
                this.isConnected = true;
                this.sessionId = data.sessionId;
                this.updateStatus('🟢 Conectado', '#4CAF50');
                this.connectButton.disabled = true;
                this.disconnectButton.disabled = false;
                
                this.addMessage(`✅ Conectado com sucesso! Session: ${this.sessionId}`, 'success');
                this.addMessage('🔄 Aguardando mensagens OPMET...', 'info');

                // Iniciar polling para simular WebSocket
                this.startMessagePolling();
            } else {
                throw new Error(data.message || 'Erro na conexão');
            }

        } catch (error) {
            console.error('❌ Erro ao conectar OPMET:', error);
            this.addMessage(`❌ Erro na conexão: ${error.message}`, 'error');
            this.updateStatus('🔴 Erro', '#f44336');
            this.connectButton.disabled = false;
        }
    }

    disconnect() {
        if (!this.isConnected) return;

        this.isConnected = false;
        this.updateStatus('🔴 Desconectado', '#f44336');
        this.connectButton.disabled = false;
        this.disconnectButton.disabled = true;

        if (this.eventSource) {
            this.eventSource.close();
            this.eventSource = null;
        }

        this.addMessage('🔌 Desconectado do OPMET', 'info');
        console.log('🔌 OPMET desconectado');
    }

    startMessagePolling() {
        console.log('📡 Conectando ao OPMET via SSE...');

        this.eventSource = new EventSource('/api/v1/opmet/stream');

        this.eventSource.onmessage = (e) => {
            if (!this.isConnected) return;
            const message = e.data;
            if (this.processedMessages.has(message)) return;
            this.processedMessages.add(message);
            this.lastNewMessageTime = Date.now();
            if (this.processedMessages.size > 200) {
                this.processedMessages.delete(this.processedMessages.values().next().value);
            }
            const cleanMessage = message.replace(/^\[\d{2}:\d{2}:\d{2}\]\s/, '');
            const isSigmet = cleanMessage.includes('SIGMET') || cleanMessage.includes(' SB');
            this.addMessage(cleanMessage, isSigmet ? 'sigmet' : 'data');
            this.updateStatus('🟢 Conectado (Real)', '#4CAF50');
        };

        this.eventSource.onerror = () => {
            this.updateStatus('🔴 Erro de Conexão', '#f44336');
        };

        this.addMessage('🔄 Conectado ao OPMET real - aguardando mensagens...', 'success');
    }

    addMessage(text, type = 'data') {
        const messageElement = document.createElement('div');
        const timestamp = new Date().toLocaleTimeString();
        
        let style = 'margin: 2px 0; padding: 2px 0;';
        let prefix = `[${timestamp}] `;

        switch (type) {
            case 'sigmet':
                style += 'color: #d32f2f; font-weight: bold; background: #ffebee; padding: 4px;';
                prefix += '🚨 ';
                break;
            case 'error':
                style += 'color: #f44336; font-weight: bold;';
                break;
            case 'success':
                style += 'color: #4CAF50; font-weight: bold;';
                break;
            case 'info':
                style += 'color: #2196F3;';
                break;
            default:
                style += 'color: #333;';
        }

        messageElement.style.cssText = style;
        messageElement.textContent = prefix + text;

        this.messageContainer.appendChild(messageElement);
        this.messageCount++;
        this.messageCountElement.textContent = `Mensagens: ${this.messageCount}`;

        // Limitar número de mensagens para performance
        if (this.messageCount > this.maxMessages) {
            const firstMessage = this.messageContainer.firstChild;
            if (firstMessage) {
                this.messageContainer.removeChild(firstMessage);
            }
        }

        // Auto-scroll para a última mensagem
        if (this.autoScroll) {
            this.messageContainer.scrollTop = this.messageContainer.scrollHeight;
        }

        console.log(`📨 OPMET [${type}]: ${text}`);
    }

    clearMessages() {
        this.messageContainer.innerHTML = '';
        this.messageCount = 0;
        this.processedMessages.clear(); // Limpar mensagens processadas
        this.messageCountElement.textContent = 'Mensagens: 0';
        this.addMessage('Mensagens limpas.', 'info');
        console.log('🗑️ Mensagens OPMET limpas');
    }

    updateStatus(text, color) {
        this.statusElement.textContent = text;
        this.statusElement.style.color = color;
    }
}

// Instância global
const opmetLiveClient = new OpmetLiveClient();

// Inicializar quando a página carregar
document.addEventListener('DOMContentLoaded', () => {
    console.log('🚀 Inicializando OPMET Live Client...');
    opmetLiveClient.init();
});

// Cleanup ao fechar página
window.addEventListener('beforeunload', () => {
    if (opmetLiveClient.isConnected) {
        opmetLiveClient.disconnect();
    }
});
