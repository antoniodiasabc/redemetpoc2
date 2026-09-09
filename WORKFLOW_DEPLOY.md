# Workflow de Deploy — REGRA OBRIGATÓRIA

## ⚠️ NUNCA fazer rebuild Docker sem antes validar o JAR

### Passo a passo

```bash
# 1. Build do JAR
mvn clean package -q -DskipTests

# 2. Testar a lógica crítica ANTES do Docker
#    (ex: regex de idade de METAR, parsing, etc.)
python3 -c "... teste da lógica ..."

# 3. Só depois de validado, rebuild Docker
docker compose up -d --build app1 app2
```

### Por quê?

- Rebuild Docker reinicia os containers → Redis perde cache → todos os aeródromos ficam marrom
- Cada rebuild desnecessário causa ~5 min de degradação visual no mapa
- Testar o JAR localmente não derruba nada em produção

### Checklist antes do rebuild

- [ ] `mvn clean package` sem erros
- [ ] Lógica nova testada com casos reais (dados de METAR, timestamps, etc.)
- [ ] Confirmar que o bug foi reproduzido e corrigido no teste antes de subir
