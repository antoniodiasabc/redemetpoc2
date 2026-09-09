# Kiro-CLI MCP Server

Servidor MCP para integrar kiro-cli com Amazon Q.

## Instalação

```bash
cd mcp-kiro
npm install
```

## Configuração no Q CLI

Adicione ao seu arquivo de configuração MCP:

```json
{
  "mcpServers": {
    "kiro-cli": {
      "command": "node",
      "args": ["/home/aodias/redemetpoc2_.bk07042026/mcp-kiro/server.js"]
    }
  }
}
```

## Uso

O servidor expõe a ferramenta `kiro_execute` que permite executar comandos do kiro-cli através do Amazon Q.
