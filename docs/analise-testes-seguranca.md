# Análise de Cobertura de Testes — Segurança e Estabilidade

Data: 2026-09-09

---

## 1. Inventário de Testes Existentes

| Arquivo | Tipo | Roda sem app? |
|---|---|---|
| `SecurityTest.java` | Integração (requer app) | ❌ — `@EnabledIfSystemProperty(security.tests=true)` |
| `EndpointContractTest.java` | Contrato (requer app) | ❌ |
| `EndpointIntegrationTest.java` | Integração (requer app) | ❌ |
| `FrontendEndpointsTest.java` | Contrato frontend (requer app) | ❌ |
| `ConcurrencyTest.java` | Concorrência (requer app) | ❌ |
| Demais (parser, cache, grib2...) | Unitários | ✅ maioria |

**Conclusão: nenhum teste de segurança roda no `mvn test` padrão** — todos dependem da aplicação estar no ar e de flags manuais.

---

## 2. O que os testes COBREM hoje

### SecurityTest.java
- ✅ Path traversal em `/data/`, `/frame/`, `/canal16frame/`, `/convection/process`
- ✅ Headers HTTP: `X-Frame-Options`, `X-Content-Type-Options`, `X-XSS-Protection`
- ❌ **NÃO testa autenticação** — os testes de `/frames` e `/canal16frames` esperam 200 sem login, o que só funcionava quando `anyRequest().permitAll()` estava ativo (configuração antiga)
- ❌ NÃO testa redirect para `/login` quando não autenticado
- ❌ NÃO testa HSTS, CSP, Referrer-Policy

### EndpointContractTest.java
- ✅ Cobre ~40 endpoints verificando status < 500 e formato JSON
- ⚠️ `sessionCookie = ""` — comentário diz "login desabilitado — anyRequest().permitAll()" — **este teste está desatualizado**: com autenticação ativa, todos os endpoints vão retornar 302 redirect para login, não 200
- ❌ NÃO faz login antes de testar — vai falhar em todos os endpoints protegidos

### EndpointIntegrationTest.java
- ✅ Faz login real (busca CSRF, autentica, guarda cookie de sessão)
- ✅ Testa health, frames, canal16frames, wind barbs, metar com sessão autenticada
- ⚠️ Usa senha hardcoded `changeme` — se `ADMIN_PASSWORD` mudar, o teste quebra
- ❌ NÃO testa comportamento sem autenticação (acesso negado)
- ❌ NÃO testa logout

### FrontendEndpointsTest.java
- ✅ Cobre todos os endpoints consumidos pelo frontend (~30 endpoints)
- ✅ Verifica CORS em endpoints críticos
- ✅ Verifica formato JSON e campos obrigatórios
- ⚠️ `sessionCookie = ""` — mesmo problema do ContractTest: **vai falhar com autenticação ativa**
- ❌ NÃO faz login

### ConcurrencyTest.java
- ✅ Faz login real antes dos testes
- ✅ Testa health + barbelas simultâneos
- ✅ Testa 3 requisições sequenciais ao health
- ❌ Concorrência muito superficial — não testa race conditions reais

---

## 3. Lacunas Críticas de Cobertura

### 3.1 Autenticação e Sessão — SEM COBERTURA
Nenhum teste verifica:
- Redirect 302 → `/login` para usuário não autenticado
- Login com credenciais erradas retorna erro (não 200)
- Brute force: após 5 tentativas, IP é bloqueado
- Session fixation: sessão é invalidada após login bem-sucedido
- Logout invalida a sessão (cookie antigo não funciona mais)
- Cookie de sessão tem flags `Secure`, `HttpOnly`, `SameSite`

### 3.2 Autorização — COBERTURA PARCIAL
- ✅ `@PreAuthorize("hasRole('ADMIN')")` no `/api/auth/register` — mas não há teste que confirme que um USER não consegue acessar
- ❌ NÃO testa que `/actuator/**` exige autenticação
- ❌ NÃO testa diferença de permissão entre roles USER e ADMIN

### 3.3 Rate Limiting — SEM COBERTURA REAL
- `ConcurrencyTest` aceita 429 como válido mas não **força** o rate limit
- Nenhum teste verifica que após N requisições o IP é bloqueado
- Nenhum teste verifica que o rate limit em memória (`ConcurrentHashMap`) é por instância (bug com 2 instâncias)

### 3.4 CSRF — SEM COBERTURA
- Nenhum teste verifica que POST sem token CSRF retorna 403
- Nenhum teste verifica que os paths ignorados (`/api/**`) realmente aceitam POST sem CSRF

### 3.5 Headers de Segurança — COBERTURA PARCIAL
- `SecurityTest` verifica 3 headers mas **não verifica**:
  - `Content-Security-Policy`
  - `Strict-Transport-Security`
  - `Referrer-Policy`
  - `X-Forwarded-Proto` (necessário para redirect correto)

---

## 4. Testes Desatualizados (vão FALHAR hoje)

| Teste | Motivo da Falha |
|---|---|
| `EndpointContractTest` | Não faz login — todos os endpoints retornam 302, não 200 |
| `FrontendEndpointsTest` | Não faz login — idem |
| `SecurityTest` (framesEndpointOk, canal16framesEndpointOk) | Espera 200 sem autenticação |

---

## 5. Risco de Cada Correção de Segurança Pendente

| Correção | Testes que podem quebrar | Risco |
|---|---|---|
| Session fixation (`sessionManagement`) | `EndpointIntegrationTest`, `ConcurrencyTest` — o cookie de sessão muda após login | **MÉDIO** — precisa atualizar os testes para pegar o novo cookie pós-login |
| Cookie flags (`Secure`, `HttpOnly`, `SameSite`) | Nenhum teste verifica isso hoje | **BAIXO** — não quebra testes existentes |
| `/api/auth/register` sair do `permitAll()` | Nenhum teste testa esse endpoint diretamente | **BAIXO** |
| `ADMIN_PASSWORD` fallback `changeme` removido | `EndpointIntegrationTest` e `ConcurrencyTest` usam `changeme` hardcoded | **ALTO** — quebra os testes se a senha mudar |

---

## 6. Recomendação Antes de Qualquer Alteração

**Ordem segura para aplicar correções:**

1. Primeiro atualizar `EndpointContractTest` e `FrontendEndpointsTest` para fazer login (como `EndpointIntegrationTest` já faz)
2. Aplicar correção de cookie flags — menor risco, não afeta fluxo de sessão
3. Aplicar session fixation — atualizar testes para capturar novo cookie pós-login
4. Só então alterar `ADMIN_PASSWORD` — atualizar senha nos testes junto

**Sem esses ajustes nos testes, qualquer correção de segurança vai quebrar a suíte de testes existente.**
