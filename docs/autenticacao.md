# Plano de Implementação — Autenticação JWT

## Visão Geral

Proteger todos os endpoints da aplicação via JWT, com sessões armazenadas no MongoDB
compartilhado entre as instâncias (app1, app2...).

---

## Backend

### 1. Dependências (pom.xml)
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.6</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.6</version>
</dependency>
```

### 2. Coleção MongoDB — `users`
```json
{
  "username": "joao.silva",
  "passwordHash": "<bcrypt>",
  "role": "VIEWER",
  "ativo": true,
  "criadoEm": "2025-01-01T00:00:00Z"
}
```
Roles: `ADMIN`, `METEOROLOGISTA`, `VIEWER`

### 3. Novos arquivos Java
| Arquivo | Responsabilidade |
|---|---|
| `JwtService.java` | gerar e validar tokens JWT |
| `JwtFilter.java` | interceptar requests e validar token antes dos controllers |
| `SecurityConfig.java` | definir rotas públicas vs protegidas |
| `AuthController.java` | endpoint `POST /auth/login` e `POST /auth/logout` |
| `UserRepository.java` | CRUD de usuários no MongoDB |

### 4. Rotas públicas (sem JWT)
```
GET  /health
GET  /actuator/health
POST /auth/login
```
Todas as demais exigem JWT válido.

## Variáveis de Ambiente (.env)
```
AUTH_ENABLED=false        # false = sem autenticação (padrão), true = exige login
JWT_SECRET=<chave-aleatoria-256bits>
JWT_EXPIRATION_HOURS=8
```

Para habilitar/desabilitar:
1. Altere `AUTH_ENABLED` no `.env`
2. `docker compose up -d`

> O restart leva ~30s. O nginx mantém o serviço durante a reinicialização das instâncias.

---

## Frontend (index.html)

### 6. Página de login
- Nova rota `/login` servindo formulário simples (usuário + senha)
- Submit faz `POST /auth/login` → recebe `{ token, expiresAt, role }`
- Salva token em `localStorage` com chave `jwt`

### 7. Wrapper global de fetch
Substituir todos os `fetch(` por `apiFetch(` — único ponto de manutenção:
```javascript
async function apiFetch(url, options = {}) {
    const token = localStorage.getItem('jwt');
    if (token) options.headers = { ...options.headers, 'Authorization': 'Bearer ' + token };
    const resp = await fetch(url, options);
    if (resp.status === 401) { localStorage.removeItem('jwt'); window.location.href = '/login'; }
    return resp;
}
```

### 8. Logout
- Botão na navbar chama `POST /auth/logout` + limpa localStorage + redireciona `/login`

---

## Impacto

| Área | Mudança |
|---|---|
| Backend | 5 novos arquivos, 1 `SecurityConfig`, 3 deps |
| Frontend | 1 página de login, 1 função wrapper, busca/replace `fetch(` → `apiFetch(` |
| Infraestrutura | 2 variáveis no `.env`, MongoDB já existe |
| Testes | atualizar testes que chamam endpoints (adicionar token mock) |

---

## Ordem de Execução

1. Backend: `JwtService` + `JwtFilter` + `SecurityConfig`
2. Backend: `AuthController` + `UserRepository`
3. Frontend: página de login + `apiFetch`
4. Frontend: busca/replace `fetch(` → `apiFetch(`
5. `.env`: `JWT_SECRET` + `JWT_EXPIRATION_HOURS`
6. Testes

---

## Observações

- Senha armazenada com **bcrypt** — nunca em texto puro
- JWT expira em 8h por padrão (configurável no `.env`)
- Com múltiplas instâncias (app1, app2) o JWT é stateless — não precisa de sessão centralizada, apenas o `JWT_SECRET` precisa ser o mesmo nas duas instâncias (garantido pelo `.env` compartilhado)
- MongoDB usado apenas para cadastro de usuários, não para sessões
