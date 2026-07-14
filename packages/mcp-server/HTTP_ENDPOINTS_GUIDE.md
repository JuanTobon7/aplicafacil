# MCP Server - HTTP Endpoints Implementation Guide

Este documento describe cómo actualizar el MCP Server para exponer endpoints HTTP que el aplicafacil-server pueda consumir.

## Arquitectura

```
aplicafacil-server (JobRecommendation.service)
        ↓ (HTTP requests)
    MCP Server (Express)
        ↓ (usa AiProviderFactory, ToolsBank)
    OpenRouter API / LLM
```

## Endpoints Requeridos

### 1. POST `/tools/fill-form`
Completa campos de formulario usando IA.

**Request:**
```json
{
  "system": "string (system prompt)",
  "prompt": "string (user prompt)"
}
```

**Response:**
```json
{
  "content": [
    {
      "type": "text",
      "text": "[{\"fieldName\":\"name\",\"value\":\"Juan\",\"confidence\":0.95,\"requires_review\":false}]"
    }
  ]
}
```

**Implementation:**
```typescript
app.post('/tools/fill-form', async (req, res) => {
  try {
    const { system, prompt } = req.body;
    const aiProvider = new OpenRouterAdapter();
    const result = await aiProvider.fillForm({ system, prompt });
    res.json({
      content: [{ type: 'text', text: result }]
    });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});
```

---

### 2. POST `/tools/embeddings`
Obtiene embeddings de texto.

**Request:**
```json
{
  "data": "string o array de strings"
}
```

**Response:**
```json
{
  "embedding": [0.123, -0.456, ...]
}
```

**Implementation:**
```typescript
app.post('/tools/embeddings', async (req, res) => {
  try {
    const { data } = req.body;
    const aiProvider = new OpenRouterAdapter();
    const embedding = await aiProvider.getEmbedding(data);
    res.json({ embedding });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});
```

---

### 3. GET `/tools/search-profile`
Busca un perfil por ID.

**Query Parameters:**
- `id` (string): El ID del perfil

**Response:**
```json
{
  "id": "profile-123",
  "name": "John Doe",
  "skills": [...],
  "experience": [...]
}
```

**Implementation:**
```typescript
app.get('/tools/search-profile', async (req, res) => {
  try {
    const { id } = req.query;
    const aiProfileTool = toolsBank.get<AiProfileTool>('aiProfileTool');
    const result = await aiProfileTool.getProfileById(id as string);
    res.json(result);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});
```

---

### 4. POST `/tools/search-people`
Busca personas según criterios.

**Request:**
```json
{
  "skills": ["JavaScript", "TypeScript"],
  "location": "Colombia",
  "experience_years": 3
}
```

**Response:**
```json
[
  {
    "id": "person-1",
    "name": "Person Name",
    "skills": [...],
    "location": "Colombia"
  }
]
```

**Implementation:**
```typescript
app.post('/tools/search-people', async (req, res) => {
  try {
    const criteria = req.body;
    const aiPeopleTool = toolsBank.get<AiSearchPeopleTool>('aiPeopleTool');
    const result = await aiPeopleTool.searchPeople(criteria);
    res.json(result);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});
```

---

## Actualizar package.json

Agregar dependencias para Express:

```json
{
  "dependencies": {
    "@modelcontextprotocol/sdk": "^x.x.x",
    "express": "^4.18.0",
    "cors": "^2.8.5",
    "axios": "^1.18.1",
    "pg": "^8.13.0",
    "zod": "^4.4.3",
    "openai": "^4.x.x"
  },
  "devDependencies": {
    "@types/express": "^4.17.x",
    "@types/cors": "^2.8.x"
  }
}
```

---

## Actualizar src/index.ts

```typescript
import express from 'express';
import cors from 'cors';
import { z } from 'zod';
import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { toolsBank } from './tools/index.js';
import { AiProfileTool } from './tools/contract/ai.search.profile.js';
import { OpenRouterAdapter } from './ai/adapter/impl/open.ai.adapter.js';

const app = express();
app.use(cors());
app.use(express.json());

// MCP Server
const mcpServer = new McpServer({
  name: 'aplicafacil-mcp-server',
  version: '1.0.0',
});

// ... registrar tools del MCP ...

// HTTP Endpoints
app.post('/tools/fill-form', async (req, res) => {
  try {
    const { system, prompt } = req.body;
    const aiProvider = new OpenRouterAdapter();
    const result = await aiProvider.fillForm({ system, prompt });
    res.json({
      content: [{ type: 'text', text: result }],
    });
  } catch (error) {
    console.error('Error in fill-form:', error);
    res.status(500).json({ error: error instanceof Error ? error.message : 'Unknown error' });
  }
});

app.post('/tools/embeddings', async (req, res) => {
  try {
    const { data } = req.body;
    const aiProvider = new OpenRouterAdapter();
    const embedding = await aiProvider.getEmbedding(data);
    res.json({ embedding });
  } catch (error) {
    console.error('Error in embeddings:', error);
    res.status(500).json({ error: error instanceof Error ? error.message : 'Unknown error' });
  }
});

app.get('/tools/search-profile', async (req, res) => {
  try {
    const { id } = req.query;
    const aiProfileTool = toolsBank.get<AiProfileTool>('aiProfileTool');
    const result = await aiProfileTool.getProfileById(id as string);
    res.json(result);
  } catch (error) {
    console.error('Error in search-profile:', error);
    res.status(500).json({ error: error instanceof Error ? error.message : 'Unknown error' });
  }
});

app.post('/tools/search-people', async (req, res) => {
  try {
    const criteria = req.body;
    const aiPeopleTool = toolsBank.get<AiSearchPeopleTool>('aiPeopleTool');
    const result = await aiPeopleTool.searchPeople(criteria);
    res.json(result);
  } catch (error) {
    console.error('Error in search-people:', error);
    res.status(500).json({ error: error instanceof Error ? error.message : 'Unknown error' });
  }
});

const PORT = process.env.PORT || 3001;
app.listen(PORT, () => {
  console.log(`🚀 MCP Server running on port ${PORT}`);
  console.log(`📍 Available endpoints:`);
  console.log(`   POST /tools/fill-form`);
  console.log(`   POST /tools/embeddings`);
  console.log(`   GET  /tools/search-profile`);
  console.log(`   POST /tools/search-people`);
});
```

---

## Configuración en aplicafacil-server

### .env
```env
MCP_SERVER_URL=http://localhost:3001
```

### app.module.ts
El McpClientModule ya está importado automáticamente a través del JobRecommendationModule.

---

## Pruebas

### Con curl:
```bash
# Fill Form
curl -X POST http://localhost:3001/tools/fill-form \
  -H "Content-Type: application/json" \
  -d '{
    "system": "Your system prompt",
    "prompt": "Your user prompt"
  }'

# Embeddings
curl -X POST http://localhost:3001/tools/embeddings \
  -H "Content-Type: application/json" \
  -d '{"data": "Some text to embed"}'

# Search Profile
curl "http://localhost:3001/tools/search-profile?id=profile-123"

# Search People
curl -X POST http://localhost:3001/tools/search-people \
  -H "Content-Type: application/json" \
  -d '{"skills": ["JavaScript"], "location": "Colombia"}'
```

---

## Notas

- El MCP Server expone tanto endpoints HTTP como herramientas MCP
- Los errores de conexión del aplicafacil-server se loguean en `McpClientService`
- Los timeouts están configurados: 30s para fill-form, 10s para embeddings y search
- Todas las requests incluyen manejo de errores y logging
