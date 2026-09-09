// Teste automatizado para verificar se polígonos ficam editáveis
const puppeteer = require('puppeteer');

async function testEditablePolygons() {
    let browser;
    try {
        browser = await puppeteer.launch({ headless: false });
        const page = await browser.newPage();
        
        // Ir para aplicação
        await page.goto('http://localhost:8082');
        await page.waitForTimeout(2000);
        
        console.log('✅ Página carregada');
        
        // Clicar em HSV Otimizado
        await page.click('button[onclick="analyzeHSVOptimized()"]');
        await page.waitForTimeout(3000);
        
        console.log('✅ HSV Otimizado executado');
        
        // Verificar se polígonos foram salvos
        const polygonsCount = await page.evaluate(() => {
            return window.hsvOptimizedPolygons ? window.hsvOptimizedPolygons.length : 0;
        });
        
        console.log(`✅ Polígonos salvos: ${polygonsCount}`);
        
        // Clicar em Criar Polígonos Editáveis
        await page.click('button[onclick="createEditablePolygons()"]');
        await page.waitForTimeout(2000);
        
        console.log('✅ Polígonos editáveis criados');
        
        // Verificar se layer editável existe
        const hasEditableLayer = await page.evaluate(() => {
            return window.editablePolygonsLayer !== null;
        });
        
        console.log(`✅ Layer editável existe: ${hasEditableLayer}`);
        
        // Verificar se interações de edição estão ativas
        const hasEditInteractions = await page.evaluate(() => {
            return window.editInteractions && window.editInteractions.length > 0;
        });
        
        console.log(`✅ Interações de edição ativas: ${hasEditInteractions}`);
        
        if (polygonsCount > 0 && hasEditableLayer && hasEditInteractions) {
            console.log('🎯 TESTE PASSOU! Polígonos são editáveis');
        } else {
            console.log('❌ TESTE FALHOU! Polígonos não são editáveis');
        }
        
    } catch (error) {
        console.error('❌ Erro no teste:', error.message);
    } finally {
        if (browser) {
            await browser.close();
        }
    }
}

// Executar teste
testEditablePolygons();
