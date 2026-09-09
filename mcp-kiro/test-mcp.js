#!/usr/bin/env node

// Teste simples do servidor MCP
import { spawn } from 'child_process';

console.log('🧪 Testando servidor MCP...');

const server = spawn('node', ['server.js'], {
  cwd: '/home/aodias/redemetpoc2_.bk07042026/mcp-kiro',
  stdio: ['pipe', 'pipe', 'pipe']
});

// Simula requisição MCP para listar ferramentas
const listToolsRequest = {
  jsonrpc: '2.0',
  id: 1,
  method: 'tools/list'
};

server.stdin.write(JSON.stringify(listToolsRequest) + '\n');

server.stdout.on('data', (data) => {
  console.log('📤 Resposta do servidor:', data.toString());
  server.kill();
});

server.stderr.on('data', (data) => {
  console.log('❌ Erro:', data.toString());
});

setTimeout(() => {
  console.log('⏰ Timeout - encerrando teste');
  server.kill();
}, 3000);
