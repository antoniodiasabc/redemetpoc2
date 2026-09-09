/**
 * OPMET Live - Painel lateral para mensagens OPMET em tempo real
 */
class OpmetLive {
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
        this.processedMessages = new Set(); // Para evitar duplicatas
        this.brasilFilter = false; // Filtro Brasil
    }

    init() {
        this.createButton();
        this.createModal();
    }

    createButton() {
        // Não criar botão na barra - será criado no mapa
    }

    createModal() {
        // Criar painel lateral direito
        this.modal = document.createElement('div');
        this.modal.style.cssText = `
            display: none;
            position: fixed;
            z-index: 10000;
            top: 220px;
            right: 10px;
            width: 480px;
            height: 75vh;
            background: linear-gradient(135deg, #1a1a1a, #2a2a2a);
            border: 2px solid #00ff00;
            border-radius: 8px;
            box-shadow: 0 0 20px rgba(0, 255, 0, 0.4);
            font-family: 'Courier New', monospace;
            color: #00ff00;
            display: flex;
            flex-direction: column;
        `;

        // Header
        const header = document.createElement('div');
        header.style.cssText = `
            background: #333;
            color: #00ff00;
            padding: 10px 15px;
            border-radius: 6px 6px 0 0;
            display: flex;
            justify-content: space-between;
            align-items: center;
            border-bottom: 1px solid #00ff00;
            cursor: move;
            user-select: none;
        `;

        const title = document.createElement('h3');
        title.textContent = '📡 OPMET Live';
        title.style.cssText = `
            margin: 0;
            color: #00ff00;
            font-size: 16px;
            font-family: 'Courier New', monospace;
        `;

        const closeButton = document.createElement('span');
        closeButton.textContent = '×';
        closeButton.style.cssText = `
            color: #ff4444;
            font-size: 24px;
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
            padding: 12px 15px;
            background: #2a2a2a;
            border-bottom: 1px solid #444;
            display: flex;
            gap: 10px;
            align-items: center;
            flex-wrap: wrap;
            font-size: 13px;
        `;

        // Status
        this.statusElement = document.createElement('span');
        this.statusElement.textContent = '🔴 Offline';
        this.statusElement.style.cssText = `
            font-weight: bold;
            color: #ff4444;
            font-size: 13px;
        `;

        // Botões compactos
        this.connectButton = document.createElement('button');
        this.connectButton.textContent = 'Conectar';
        this.connectButton.style.cssText = `
            padding: 6px 12px;
            background: #00aa00;
            color: white;
            border: none;
            border-radius: 4px;
            cursor: pointer;
            font-size: 12px;
        `;
        this.connectButton.onclick = () => this.connect();

        this.disconnectButton = document.createElement('button');
        this.disconnectButton.textContent = 'Desconectar';
        this.disconnectButton.disabled = true;
        this.disconnectButton.style.cssText = `
            padding: 6px 12px;
            background: #aa0000;
            color: white;
            border: none;
            border-radius: 4px;
            cursor: pointer;
            font-size: 12px;
        `;
        this.disconnectButton.onclick = () => this.disconnect();

        this.clearButton = document.createElement('button');
        this.clearButton.textContent = 'Limpar';
        this.clearButton.style.cssText = `
            padding: 6px 12px;
            background: #666;
            color: white;
            border: none;
            border-radius: 4px;
            cursor: pointer;
            font-size: 12px;
        `;
        this.clearButton.onclick = () => this.clearMessages();

        // Contador de mensagens
        this.messageCountElement = document.createElement('span');
        this.messageCountElement.textContent = 'Mensagens: 0';
        this.messageCountElement.style.cssText = `
            font-size: 11px;
            color: #888;
            margin-left: auto;
        `;

        // Auto-scroll checkbox
        const autoScrollLabel = document.createElement('label');
        autoScrollLabel.style.cssText = `
            display: flex;
            align-items: center;
            gap: 5px;
            font-size: 11px;
            color: #888;
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
            font-size: 11px;
            color: #888;
        `;

        this.iwxxmCheckbox = document.createElement('input');
        this.iwxxmCheckbox.type = 'checkbox';
        this.iwxxmCheckbox.checked = false; // Default: sem parâmetro

        iwxxmLabel.appendChild(this.iwxxmCheckbox);
        iwxxmLabel.appendChild(document.createTextNode('IWXXM'));

        // Checkbox Brasil
        const brasilLabel = document.createElement('label');
        brasilLabel.style.cssText = `
            display: flex;
            align-items: center;
            font-size: 11px;
            color: #ccc;
            margin-left: 8px;
            cursor: pointer;
        `;
        
        this.brasilCheckbox = document.createElement('input');
        this.brasilCheckbox.type = 'checkbox';
        this.brasilCheckbox.checked = false;
        this.brasilCheckbox.style.cssText = `
            margin-right: 4px;
            cursor: pointer;
        `;
        this.brasilCheckbox.onchange = () => {
            this.brasilFilter = this.brasilCheckbox.checked;
            console.log('🇧🇷 Filtro Brasil:', this.brasilFilter ? 'ATIVO' : 'INATIVO');
        };
        
        brasilLabel.appendChild(this.brasilCheckbox);
        brasilLabel.appendChild(document.createTextNode('🇧🇷 Só Brasil'));

        controls.appendChild(this.statusElement);
        controls.appendChild(this.connectButton);
        controls.appendChild(this.disconnectButton);
        controls.appendChild(this.clearButton);
        controls.appendChild(autoScrollLabel);
        controls.appendChild(iwxxmLabel);
        controls.appendChild(brasilLabel);
        controls.appendChild(this.messageCountElement);

        // Container de mensagens
        this.messageContainer = document.createElement('div');
        this.messageContainer.style.cssText = `
            flex: 1;
            overflow-y: auto;
            padding: 10px 15px;
            background: #1a1a1a;
            font-family: 'Courier New', monospace;
            font-size: 13px;
            line-height: 1.4;
            color: #00ff00;
            border-radius: 0 0 6px 6px;
        `;

        // Montar modal
        this.modal.appendChild(header);
        this.modal.appendChild(controls);
        this.modal.appendChild(this.messageContainer);
        document.body.appendChild(this.modal);

        // Tornar modal arrastável
        this.makeDraggable(header);

        // Adicionar mensagem inicial
        this.addMessage('📡 OPMET Live iniciado. Clique "Conectar" para começar.', 'info');
    }

    makeDraggable(header) {
        let isDragging = false;
        let dragOffset = { x: 0, y: 0 };

        header.addEventListener('mousedown', (e) => {
            isDragging = true;
            dragOffset.x = e.clientX - this.modal.offsetLeft;
            dragOffset.y = e.clientY - this.modal.offsetTop;
            e.preventDefault();
        });

        document.addEventListener('mousemove', (e) => {
            if (isDragging) {
                let newLeft = e.clientX - dragOffset.x;
                let newTop = e.clientY - dragOffset.y;
                
                // Manter dentro da tela
                newLeft = Math.max(0, Math.min(newLeft, window.innerWidth - this.modal.offsetWidth));
                newTop = Math.max(0, Math.min(newTop, window.innerHeight - this.modal.offsetHeight));
                
                this.modal.style.left = newLeft + 'px';
                this.modal.style.top = newTop + 'px';
                this.modal.style.right = 'auto'; // Remove right fixo
            }
        });

        document.addEventListener('mouseup', () => {
            isDragging = false;
        });
    }

    showModal() {
        this.modal.style.display = 'flex';
    }

    hideModal() {
        this.modal.style.display = 'none';
        if (this.isConnected) {
            this.disconnect();
        }
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

                // Iniciar polling para mensagens reais
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
        if (this.eventSource) {
            this.eventSource.close();
            this.eventSource = null;
        }

        this.updateStatus('🔴 Desconectado', '#f44336');
        this.connectButton.disabled = false;
        this.disconnectButton.disabled = true;
        
        this.addMessage('🔌 Desconectado do OPMET.', 'info');
    }

    startMessagePolling() {
        console.log('📡 Conectando ao OPMET via SSE...');

        this.eventSource = new EventSource('/api/v1/opmet/stream');

        this.eventSource.onmessage = (e) => {
            if (!this.isConnected) return;
            const message = e.data;
            if (this.processedMessages.has(message)) return;
            this.processedMessages.add(message);
            const cleanMessage = message.replace(/^\[\d{2}:\d{2}:\d{2}\]\s/, '');
            if (this.brasilFilter && !cleanMessage.includes(' SB')) return;
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
        
        let style = 'margin: 2px 0; padding: 2px 0; word-wrap: break-word;';
        let prefix = `[${timestamp}] `;

        switch (type) {
            case 'sigmet':
                style += 'color: #ff8c00; font-weight: bold; background: rgba(255, 140, 0, 0.1); padding: 4px; border-left: 3px solid #ff8c00;';
                prefix += '🚨 ';
                break;
            case 'success':
                style += 'color: #44ff44; font-weight: bold;';
                prefix += '✅ ';
                break;
            case 'error':
                style += 'color: #ff4444; font-weight: bold;';
                prefix += '❌ ';
                break;
            case 'info':
                style += 'color: #4488ff;';
                prefix += 'ℹ️ ';
                break;
            default:
                style += 'color: #00ff00;';
                prefix += '📨 ';
        }

        messageElement.style.cssText = style;
        messageElement.textContent = prefix + text;

        this.messageContainer.appendChild(messageElement);
        this.messageCount++;

        // Limitar número de mensagens
        while (this.messageContainer.children.length > this.maxMessages) {
            this.messageContainer.removeChild(this.messageContainer.firstChild);
        }

        // Auto-scroll
        if (this.autoScroll) {
            this.messageContainer.scrollTop = this.messageContainer.scrollHeight;
        }

        // Atualizar contador
        this.messageCountElement.textContent = `Mensagens: ${this.messageCount}`;
    }

    updateStatus(text, color) {
        this.statusElement.textContent = text;
        this.statusElement.style.color = color;
    }

    clearMessages() {
        this.messageContainer.innerHTML = '';
        this.messageCount = 0;
        this.processedMessages.clear(); // Limpar mensagens processadas
        this.messageCountElement.textContent = 'Mensagens: 0';
        this.addMessage('Mensagens limpas.', 'info');
        console.log('🗑️ Mensagens OPMET limpas');
    }
}

// Inicializar quando DOM estiver pronto
document.addEventListener('DOMContentLoaded', () => {
    const opmetLive = new OpmetLive();
    opmetLive.init();
    
    // Expor globalmente para o controle do mapa
    window.opmetLive = opmetLive;
});
