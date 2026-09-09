# Regras de trabalho — Amazon Q

## Fluxo obrigatório antes de qualquer mudança
1. Analisar logs e código relevante
2. Formular hipótese e explicar ao usuário
3. Aguardar confirmação
4. Só então modificar

## Nunca
- Reverter código que está funcionando sem checar logs primeiro
- Assumir que 0 resultados = bug no código (pode ser conectividade, token expirado, etc.)
- Fazer múltiplas mudanças encadeadas sem validar cada uma

## Projeto
- Stack: Spring Boot + Oracle (JPA) + MongoDB + Redis + OpenCV + Docker
- Deploy: `mvn package -DskipTests -q` → `docker cp jar app-1 && app-2` → `docker start`
- `System.loadLibrary` em static block = correto, não é bug
- `new Mat()`, `new Scalar()`, `new Color()` etc = objetos de lib, não são beans Spring
