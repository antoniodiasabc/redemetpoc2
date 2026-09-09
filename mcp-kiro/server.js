#!/usr/bin/env node

import { Server } from '@modelcontextprotocol/sdk/server/index.js';
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import { CallToolRequestSchema, ListToolsRequestSchema } from '@modelcontextprotocol/sdk/types.js';
import { exec } from 'child_process';
import { promisify } from 'util';

const execAsync = promisify(exec);

const server = new Server(
  {
    name: 'kiro-mcp-server',
    version: '1.0.0',
  },
  {
    capabilities: {
      tools: {},
    },
  }
);

server.setRequestHandler(ListToolsRequestSchema, async () => {
  return {
    tools: [
      {
        name: 'kiro_execute',
        description: 'Execute kiro-cli commands',
        inputSchema: {
          type: 'object',
          properties: {
            command: {
              type: 'string',
              description: 'Kiro-cli command to execute'
            },
            args: {
              type: 'array',
              items: { type: 'string' },
              description: 'Command arguments'
            }
          },
          required: ['command']
        }
      }
    ]
  };
});

server.setRequestHandler(CallToolRequestSchema, async (request) => {
  if (request.params.name === 'kiro_execute') {
    const { command, args = [] } = request.params.arguments;
    
    try {
      const kiroPath = '/mnt/c/Users/aodias/AppData/Local/Kiro-Cli/kiro-cli.exe';
      const fullCommand = `"${kiroPath}" ${command} ${args.join(' ')}`.trim();
      const { stdout, stderr } = await execAsync(fullCommand);
      
      return {
        content: [
          {
            type: 'text',
            text: stdout || stderr || 'Command executed successfully'
          }
        ]
      };
    } catch (error) {
      return {
        content: [
          {
            type: 'text',
            text: `Error: ${error.message}`
          }
        ],
        isError: true
      };
    }
  }
  
  throw new Error(`Unknown tool: ${request.params.name}`);
});

async function main() {
  const transport = new StdioServerTransport();
  await server.connect(transport);
}

main().catch(console.error);
