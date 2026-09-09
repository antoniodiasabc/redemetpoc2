#!/usr/bin/env node

import { exec } from 'child_process';
import { promisify } from 'util';

const execAsync = promisify(exec);

async function testKiroCli() {
  console.log('🧪 Testando kiro-cli...');
  
  try {
    const kiroPath = '/mnt/c/Users/aodias/AppData/Local/Kiro-Cli/kiro-cli.exe';
    const { stdout, stderr } = await execAsync(`"${kiroPath}" --version`);
    
    console.log('✅ Kiro-cli encontrado!');
    console.log('📋 Versão:', stdout.trim());
    
    // Teste comando help
    const { stdout: help } = await execAsync(`"${kiroPath}" --help`);
    console.log('✅ Comando help funcionando');
    console.log('📋 Primeiras linhas:', help.split('\n').slice(0, 3).join('\n'));
    
  } catch (error) {
    console.log('❌ Erro:', error.message);
  }
}

testKiroCli();
