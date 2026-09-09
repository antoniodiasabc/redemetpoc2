// Animação de Vento - Satélite + GRIB2
class WindAnimation {
    constructor() {
        this.isPlaying = false;
        this.currentFrame = 0;
        this.frames = [];
        this.intervalId = null;
        this.speed = 2; // FPS
        this.container = null;
    }

    async loadAnimation(hours = 6) {
        try {
            console.log('🔄 Carregando animação de vento...');
            const response = await fetch(`/api/v1/animation/satellite-wind?hours=${hours}`);
            const data = await response.json();
            
            if (data.error) {
                throw new Error(data.message);
            }
            
            this.frames = data.satellite_frames.map((img, i) => ({
                satellite: img,
                winds: data.wind_overlays[i] || { wind_barbs: [] },
                timestamp: data.wind_overlays[i]?.timestamp || ''
            }));
            
            console.log(`✅ ${this.frames.length} frames carregados`);
            return true;
            
        } catch (error) {
            console.error('❌ Erro ao carregar animação:', error);
            return false;
        }
    }

    createControls() {
        const controlsHtml = `
            <div id="wind-animation-controls" style="margin: 10px 0; padding: 10px; background: rgba(0,0,0,0.8); border-radius: 5px;">
                <button id="play-btn" onclick="windAnimation.togglePlay()">▶️ Play</button>
                <button onclick="windAnimation.stop()">⏹️ Stop</button>
                <button onclick="windAnimation.previousFrame()">⏮️</button>
                <button onclick="windAnimation.nextFrame()">⏭️</button>
                <span style="margin-left: 15px; color: white;">
                    Velocidade: <input type="range" id="speed-control" min="1" max="5" value="2" 
                                     onchange="windAnimation.setSpeed(this.value)">
                    <span id="speed-value">2</span> FPS
                </span>
                <span style="margin-left: 15px; color: white;">
                    Frame: <span id="frame-counter">0/0</span>
                </span>
                <div style="margin-top: 5px; color: #ccc; font-size: 12px;">
                    <span id="frame-timestamp"></span>
                </div>
            </div>
        `;
        return controlsHtml;
    }

    show() {
        if (!this.container) {
            this.container = document.getElementById('wind-animation-container');
            if (!this.container) {
                console.error('Container wind-animation-container não encontrado');
                return;
            }
        }

        // Criar controles VISÍVEIS
        this.container.innerHTML = `
            <div style="background: #333; color: white; padding: 15px; margin: 10px 0; border-radius: 8px; border: 2px solid #4CAF50;">
                <h3 style="margin: 0 0 10px 0; color: #4CAF50;">🎬 Controles da Animação de Vento</h3>
                <div style="display: flex; gap: 10px; align-items: center; flex-wrap: wrap;">
                    <button id="play-btn" onclick="windAnimation.togglePlay()" 
                            style="background: #4CAF50; color: white; border: none; padding: 10px 15px; border-radius: 4px; cursor: pointer; font-size: 14px;">
                        ▶️ Play
                    </button>
                    <button onclick="windAnimation.stop()" 
                            style="background: #f44336; color: white; border: none; padding: 10px 15px; border-radius: 4px; cursor: pointer; font-size: 14px;">
                        ⏹️ Stop
                    </button>
                    <button onclick="windAnimation.previousFrame()" 
                            style="background: #2196F3; color: white; border: none; padding: 10px 15px; border-radius: 4px; cursor: pointer; font-size: 14px;">
                        ⏮️ Anterior
                    </button>
                    <button onclick="windAnimation.nextFrame()" 
                            style="background: #2196F3; color: white; border: none; padding: 10px 15px; border-radius: 4px; cursor: pointer; font-size: 14px;">
                        ⏭️ Próximo
                    </button>
                </div>
                <div style="margin-top: 10px; display: flex; gap: 15px; align-items: center; flex-wrap: wrap;">
                    <span>Velocidade: 
                        <input type="range" id="speed-control" min="1" max="5" value="2" 
                               onchange="windAnimation.setSpeed(this.value)"
                               style="margin: 0 5px;">
                        <span id="speed-value">2</span> FPS
                    </span>
                    <span>Frame: <span id="frame-counter" style="color: #4CAF50; font-weight: bold;">0/0</span></span>
                </div>
                <div style="margin-top: 5px; font-size: 12px; color: #ccc;">
                    <span id="frame-timestamp">Carregando...</span>
                </div>
            </div>
        `;
        
        this.container.style.display = 'block';
        
        // Carregar animação
        this.loadAnimation().then(success => {
            if (success) {
                this.updateFrameCounter();
                this.renderCurrentFrame();
            }
        });
    }

    hide() {
        this.stop();
        if (this.container) {
            this.container.style.display = 'none';
        }
    }

    togglePlay() {
        if (this.isPlaying) {
            this.pause();
        } else {
            this.play();
        }
    }

    play() {
        if (this.frames.length === 0) return;
        
        this.isPlaying = true;
        document.getElementById('play-btn').innerHTML = '⏸️ Pause';
        
        this.intervalId = setInterval(() => {
            this.nextFrame();
        }, 1000 / this.speed);
    }

    pause() {
        this.isPlaying = false;
        document.getElementById('play-btn').innerHTML = '▶️ Play';
        
        if (this.intervalId) {
            clearInterval(this.intervalId);
            this.intervalId = null;
        }
    }

    stop() {
        this.pause();
        this.currentFrame = 0;
        this.updateFrameCounter();
        this.renderCurrentFrame();
    }

    nextFrame() {
        if (this.frames.length === 0) return;
        
        this.currentFrame = (this.currentFrame + 1) % this.frames.length;
        this.updateFrameCounter();
        this.renderCurrentFrame();
    }

    previousFrame() {
        if (this.frames.length === 0) return;
        
        this.currentFrame = this.currentFrame === 0 ? this.frames.length - 1 : this.currentFrame - 1;
        this.updateFrameCounter();
        this.renderCurrentFrame();
    }

    setSpeed(newSpeed) {
        this.speed = parseInt(newSpeed);
        document.getElementById('speed-value').textContent = this.speed;
        
        // Reiniciar animação com nova velocidade se estiver tocando
        if (this.isPlaying) {
            this.pause();
            this.play();
        }
    }

    updateFrameCounter() {
        const counter = document.getElementById('frame-counter');
        const timestamp = document.getElementById('frame-timestamp');
        
        if (counter) {
            counter.textContent = `${this.currentFrame + 1}/${this.frames.length}`;
        }
        
        if (timestamp && this.frames[this.currentFrame]) {
            const frameTime = this.frames[this.currentFrame].timestamp;
            timestamp.textContent = frameTime ? `Horário: ${frameTime}` : '';
        }
    }

    renderCurrentFrame() {
        if (this.frames.length === 0) return;
        
        const frame = this.frames[this.currentFrame];
        
        // Atualizar imagem de satélite (simulado - na prática usaria a imagem real)
        console.log(`🖼️ Frame ${this.currentFrame + 1}: ${frame.satellite}`);
        
        // Atualizar barbelas de vento no mapa
        if (frame.winds && frame.winds.wind_barbs) {
            this.updateWindBarbs(frame.winds.wind_barbs);
        }
    }

    updateWindBarbs(windBarbs) {
        console.log(`💨 Tentando atualizar ${windBarbs.length} barbelas de vento`);
        
        // Usar o sistema existente de barbelas
        if (typeof loadWindBarbs === 'function') {
            // Simular seleção de nível e carregar barbelas
            const levelSelect = document.getElementById('windLevelSelect');
            if (levelSelect && !levelSelect.value) {
                levelSelect.value = 'surface'; // Definir nível padrão
            }
            loadWindBarbs(); // Usar função existente
            console.log('✅ Barbelas carregadas via sistema existente');
        } else {
            console.log('⚠️ Função loadWindBarbs não encontrada, usando fallback');
            // Fallback: mostrar dados no console
            console.log('Dados de vento:', windBarbs.slice(0, 5)); // Mostrar apenas 5 primeiros
        }
    }
}

// Instância global
const windAnimation = new WindAnimation();

// Animação Realçada - cópia da WindAnimation para imagens REDEMET
class RealcadaAnimation {
    constructor() {
        this.isPlaying = false;
        this.currentFrame = 0;
        this.frames = [];
        this.intervalId = null;
        this.speed = 2;
        this.container = null;
        this.radarLayer = null; // Referência para a camada única
        this.isCleared = false; // Flag para saber se foi limpo
    }

    async loadAnimation(hours = 6) {
        try {
            console.log('🌈 Carregando animação realçada...');
            
            // Buscar imagens reais do servidor
            const response = await fetch('/api/v1/animation/realcada-images?count=12');
            const data = await response.json();
            
            if (data.images && data.images.length > 0) {
                this.frames = data.images.map(img => ({
                    image: img.url,
                    timestamp: img.timestamp,
                    filename: img.filename
                }));
                console.log(`✅ ${this.frames.length} frames realçados carregados do servidor`);
                return true;
            } else {
                console.log('⚠️ Nenhuma imagem encontrada no servidor');
                return false;
            }
            
        } catch (error) {
            console.error('❌ Erro ao carregar animação realçada:', error);
            return false;
        }
    }

    show() {
        this.isCleared = false; // Resetar flag quando mostrar novamente
        
        if (!this.container) {
            this.container = document.getElementById('realcada-animation-container');
            if (!this.container) {
                console.error('Container realcada-animation-container não encontrado');
                return;
            }
        }

        this.container.innerHTML = `
            <div style="background: #333; color: white; padding: 15px; margin: 10px 0; border-radius: 8px; border: 2px solid #FF9800;">
                <h3 style="margin: 0 0 10px 0; color: #FF9800;">🌈 Controles da Animação Realçada</h3>
                <div style="display: flex; gap: 10px; align-items: center; flex-wrap: wrap;">
                    <button id="realcada-play-btn" onclick="realcadaAnimation.togglePlay()" 
                            style="background: #FF9800; color: white; border: none; padding: 10px 15px; border-radius: 4px; cursor: pointer; font-size: 14px;">
                        ▶️ Play
                    </button>
                    <button onclick="realcadaAnimation.stop()" 
                            style="background: #f44336; color: white; border: none; padding: 10px 15px; border-radius: 4px; cursor: pointer; font-size: 14px;">
                        ⏹️ Stop
                    </button>
                    <button onclick="realcadaAnimation.clear()" 
                            style="background: #9E9E9E; color: white; border: none; padding: 10px 15px; border-radius: 4px; cursor: pointer; font-size: 14px;">
                        🗑️ Limpar
                    </button>
                    <button onclick="realcadaAnimation.previousFrame()" 
                            style="background: #2196F3; color: white; border: none; padding: 10px 15px; border-radius: 4px; cursor: pointer; font-size: 14px;">
                        ⏮️ Anterior
                    </button>
                    <button onclick="realcadaAnimation.nextFrame()" 
                            style="background: #2196F3; color: white; border: none; padding: 10px 15px; border-radius: 4px; cursor: pointer; font-size: 14px;">
                        ⏭️ Próximo
                    </button>
                </div>
                <div style="margin-top: 10px; display: flex; gap: 15px; align-items: center; flex-wrap: wrap;">
                    <span>Velocidade: 
                        <input type="range" id="realcada-speed-control" min="1" max="5" value="2" 
                               onchange="realcadaAnimation.setSpeed(this.value)"
                               style="margin: 0 5px;">
                        <span id="realcada-speed-value">2</span> FPS
                    </span>
                    <span>Opacidade: 
                        <input type="range" id="realcada-opacity-control" min="10" max="100" value="50" 
                               onchange="realcadaAnimation.setOpacity(this.value)"
                               style="margin: 0 5px;">
                        <span id="realcada-opacity-value">50</span>%
                    </span>
                    <span>Frame: <span id="realcada-frame-counter" style="color: #FF9800; font-weight: bold;">0/0</span></span>
                </div>
                <div style="margin-top: 5px; font-size: 12px; color: #ccc;">
                    <span id="realcada-frame-timestamp">Carregando...</span>
                </div>
            </div>
        `;
        
        this.container.style.display = 'block';
        
        this.loadAnimation().then(success => {
            if (success) {
                this.updateFrameCounter();
                this.renderCurrentFrame();
            }
        });
    }

    hide() {
        this.stop();
        if (this.container) {
            this.container.style.display = 'none';
        }
    }

    togglePlay() {
        if (this.isPlaying) {
            this.pause();
        } else {
            this.play();
        }
    }

    play() {
        if (this.frames.length === 0) return;
        
        this.isPlaying = true;
        document.getElementById('realcada-play-btn').innerHTML = '⏸️ Pause';
        
        this.intervalId = setInterval(() => {
            this.nextFrame();
        }, 1000 / this.speed);
    }

    pause() {
        this.isPlaying = false;
        document.getElementById('realcada-play-btn').innerHTML = '▶️ Play';
        
        if (this.intervalId) {
            clearInterval(this.intervalId);
            this.intervalId = null;
        }
    }

    stop() {
        this.pause();
        this.currentFrame = 0;
        this.updateFrameCounter();
        this.renderCurrentFrame();
    }

    nextFrame() {
        if (this.frames.length === 0) return;
        
        this.currentFrame = (this.currentFrame + 1) % this.frames.length;
        this.updateFrameCounter();
        this.renderCurrentFrame();
    }

    previousFrame() {
        if (this.frames.length === 0) return;
        
        this.currentFrame = this.currentFrame === 0 ? this.frames.length - 1 : this.currentFrame - 1;
        this.updateFrameCounter();
        this.renderCurrentFrame();
    }

    setSpeed(newSpeed) {
        this.speed = parseInt(newSpeed);
        document.getElementById('realcada-speed-value').textContent = this.speed;
        
        if (this.isPlaying) {
            this.pause();
            this.play();
        }
    }

    setOpacity(newOpacity) {
        const opacity = parseInt(newOpacity) / 100; // Converter para 0-1
        document.getElementById('realcada-opacity-value').textContent = newOpacity;
        
        // Atualizar opacidade da camada única
        if (this.radarLayer) {
            this.radarLayer.setOpacity(opacity);
            console.log(`🎨 Opacidade alterada para: ${newOpacity}%`);
        }
    }

    clear() {
        console.log('🗑️ Removendo camada realçada...');
        this.stop();
        this.isCleared = true; // Marcar como limpo
        
        // Remover a camada completamente
        if (this.radarLayer) {
            map.removeLayer(this.radarLayer);
            this.radarLayer = null; // Limpar referência para criar nova depois
            console.log('✅ Camada realçada removida completamente');
        }
        
        // Esconder controles
        this.hide();
    }

    updateFrameCounter() {
        const counter = document.getElementById('realcada-frame-counter');
        const timestamp = document.getElementById('realcada-frame-timestamp');
        
        if (counter) {
            counter.textContent = `${this.currentFrame + 1}/${this.frames.length}`;
        }
        
        if (timestamp && this.frames[this.currentFrame]) {
            const frame = this.frames[this.currentFrame];
            
            // Extrair data do nome do arquivo: realcada_YYYYMMDDHHMM.png
            try {
                const filename = frame.filename;
                const dateStr = filename.substring(9, 21); // YYYYMMDDHHMM
                const year = dateStr.substring(0, 4);
                const month = dateStr.substring(4, 6);
                const day = dateStr.substring(6, 8);
                const hour = dateStr.substring(8, 10);
                const minute = dateStr.substring(10, 12);
                
                // Criar data no formato brasileiro
                const dateFormatted = `${day}/${month}/${year}, ${hour}:${minute}:00`;
                timestamp.textContent = `Horário: ${dateFormatted}`;
            } catch (e) {
                // Fallback para timestamp ISO se houver erro
                const frameTime = frame.timestamp;
                if (frameTime) {
                    const date = new Date(frameTime);
                    timestamp.textContent = `Horário: ${date.toLocaleString('pt-BR')}`;
                } else {
                    timestamp.textContent = 'Horário: N/A';
                }
            }
        }
    }

    renderCurrentFrame() {
        if (this.frames.length === 0) return;
        
        const frame = this.frames[this.currentFrame];
        console.log(`🌈 Frame ${this.currentFrame + 1}/${this.frames.length}: ${frame.filename}`);
        console.log(`📡 Pedindo imagem: ${frame.image}`);
        
        // Testar se a imagem existe primeiro
        fetch(frame.image, { method: 'HEAD' })
            .then(response => {
                console.log(`📥 Backend retornou: ${response.status} para ${frame.image}`);
                
                if (response.status === 200) {
                    console.log(`✅ Imagem encontrada! Plotando: ${frame.filename}`);
                    this.plotImage(frame);
                } else {
                    console.log(`❌ Imagem não encontrada (${response.status}): ${frame.filename}`);
                }
            })
            .catch(error => {
                console.log(`❌ Erro ao buscar imagem: ${error.message}`);
            });
    }
    
    plotImage(frame) {
        // Se foi limpo, não plotar mais
        if (this.isCleared) {
            console.log('🚫 Animação foi limpa, não plotando');
            return;
        }
        
        try {
            // Procurar OU criar a camada realçada (sempre a mesma)
            if (!this.radarLayer) {
                console.log('🔄 Criando camada realçada única...');
                this.radarLayer = new ol.layer.Image({
                    source: new ol.source.ImageStatic({
                        url: frame.image,
                        imageExtent: ol.proj.transformExtent([-99.75, -55, -25.25, 20], 'EPSG:4326', 'EPSG:3857'),
                        attributions: 'REDEMET/DECEA Realçada - ' + frame.filename
                    }),
                    visible: true,
                    opacity: 0.5,
                    zIndex: 1
                });
                map.addLayer(this.radarLayer);
                console.log(`🗺️ Camada única criada: ${frame.filename}`);
            } else {
                // Apenas trocar a fonte da imagem na MESMA camada
                console.log(`🔄 Trocando fonte da mesma camada: ${frame.filename}`);
                const newSource = new ol.source.ImageStatic({
                    url: frame.image,
                    imageExtent: ol.proj.transformExtent([-99.75, -55, -25.25, 20], 'EPSG:4326', 'EPSG:3857'),
                    attributions: 'REDEMET/DECEA Realçada - ' + frame.filename
                });
                this.radarLayer.setSource(newSource);
                this.radarLayer.setVisible(true); // Garantir que fica visível
                console.log(`🗺️ Fonte trocada na mesma camada: ${frame.filename}`);
            }
            
            console.log(`🎬 Próximo frame será: ${this.getNextFrameName()}`);
            
        } catch (error) {
            console.error('❌ Erro ao plotar imagem:', error);
        }
    }
    
    getNextFrameName() {
        if (this.frames.length === 0) return 'N/A';
        const nextIndex = (this.currentFrame + 1) % this.frames.length;
        return this.frames[nextIndex] ? this.frames[nextIndex].filename : 'N/A';
    }

    addNewFrame(imageData) {
        console.log('🆕 Adicionando novo frame à animação:', imageData.filename);
        
        // Adicionar nova imagem ao final
        this.frames.push({
            image: imageData.url,
            timestamp: imageData.timestamp,
            filename: imageData.filename
        });
        
        // Remover a mais antiga (manter apenas 12 frames)
        if (this.frames.length > 12) {
            const removed = this.frames.shift();
            console.log('🗑️ Removendo frame mais antigo:', removed.filename);
            
            // Ajustar currentFrame se necessário
            if (this.currentFrame > 0) {
                this.currentFrame--;
            }
        }
        
        // Atualizar contador de frames
        this.updateFrameCounter();
        
        console.log(`✅ Frame adicionado. Total: ${this.frames.length} frames`);
        
        // Se a animação estiver rodando, continuar normalmente
        // Se estiver parada, pode opcionalmente mostrar a nova imagem
        if (!this.isPlaying && this.radarLayer) {
            // Mostrar a imagem mais recente
            this.currentFrame = this.frames.length - 1;
            this.renderCurrentFrame();
        }
    }
}

// Instância global da animação realçada
const realcadaAnimation = new RealcadaAnimation();

// Função para o botão da interface realçada
function toggleRealcadaAnimation() {
    console.log('🌈 toggleRealcadaAnimation chamada');
    
    const container = document.getElementById('realcada-animation-container');
    
    if (!container) {
        alert('❌ Container realçada não encontrado!');
        return;
    }
    
    if (container.style.display === 'none' || !container.style.display) {
        console.log('👁️ Mostrando animação realçada');
        realcadaAnimation.show();
    } else {
        console.log('🙈 Escondendo animação realçada');
        realcadaAnimation.hide();
    }
}

// Função para o botão da interface
function toggleWindAnimation() {
    console.log('🔄 toggleWindAnimation chamada');
    
    const container = document.getElementById('wind-animation-container');
    console.log('📦 Container encontrado:', container);
    
    if (!container) {
        alert('❌ Container não encontrado!');
        return;
    }
    
    if (container.style.display === 'none' || !container.style.display) {
        console.log('👁️ Mostrando animação');
        container.style.display = 'block';
        container.innerHTML = '<div style="padding: 20px; background: yellow; border: 2px solid red;">🎬 ANIMAÇÃO CARREGANDO...</div>';
        
        // Tentar carregar a animação
        try {
            windAnimation.show();
        } catch (error) {
            console.error('❌ Erro ao mostrar animação:', error);
            container.innerHTML = '<div style="padding: 20px; background: red; color: white;">❌ Erro: ' + error.message + '</div>';
        }
    } else {
        console.log('🙈 Escondendo animação');
        windAnimation.hide();
    }
}
