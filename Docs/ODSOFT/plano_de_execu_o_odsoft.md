Sim, o enunciado é genérico. A forma de o tornar executável é tratá-lo como um projeto de **engenharia do processo**, não como uma simples melhoria do backend.

## O que o projeto realmente pede

No final devem conseguir demonstrar:

> “Antes fazíamos isto manualmente e sem métricas. Agora qualquer alteração passa por um processo automático que compila, testa, mede qualidade, cria um artefacto e consegue ser instalado em ambientes diferentes.”

## Estado atual identificado

No vosso repositório:

- Spring Boot 3.2.5, Java 17 e Maven em `pom.xml`.
- Existem **102 testes**, todos verdes na execução atual.
- Há testes de domínio, repositórios, serviços e alguns controllers.
- Não encontrei configuração de CI/CD.
- Não encontrei JaCoCo, PIT, Checkstyle, SpotBugs, Sonar ou Docker.
- O Maven Wrapper está incompleto: falta `.mvn/wrapper/maven-wrapper.properties`.
- A matriz da Fase 2 mostra várias user stories sem testes.

Isto já fornece uma boa baseline para o relatório.

## Organização recomendada

Criem um board com estes work packages:

### WP1: System-as-is e baseline

Documentar, com evidência:

- Como se compila e executa atualmente.
- Como são executados os testes.
- Que tipos de testes existem.
- Quantidade de testes por tipo.
- Tempo de execução.
- Cobertura atual.
- Falhas ou limitações do processo atual.
- Como se configura a base de dados, uploads e autenticação.
- Como se faria atualmente um deployment manual.

Entregáveis:

- `System-as-is.md`
- Diagrama do processo atual.
- Tabela de testes existentes.
- Relatório inicial de cobertura.
- Lista de problemas e riscos.

### WP2: System-to-be

Definir o processo desejado:

```text
Commit / Pull Request
        |
        v
Compilação
        |
        v
Análise estática
        |
        v
Testes unitários
        |
        v
Cobertura + mutation testing
        |
        v
Testes de integração
        |
        v
Empacotamento JAR/Docker
        |
        v
Publicação de artefacto
        |
        v
Deploy para development/staging/production
```

Para cada etapa indiquem:

- Objetivo.
- Ferramenta.
- Entrada e saída.
- Critério de aprovação.
- O que acontece quando falha.

Entregáveis:

- `System-to-be.md`
- Diagrama da pipeline.
- Decisões técnicas, por exemplo ADRs.
- Estratégia de ambientes.

### WP3: Pipeline CI/CD

Sugestão pragmática:

- GitHub Actions.
- Maven para build e testes.
- JaCoCo para cobertura.
- PIT para mutation testing.
- Checkstyle ou Spotless para estilo.
- SpotBugs ou PMD para análise estática.
- GitHub Artifacts para guardar o JAR e relatórios.
- Docker para empacotar a aplicação.
- Docker Compose para execução repetível localmente.

A pipeline deve ter pelo menos:

1. `compile`
2. `test`
3. `quality`
4. `coverage`
5. `mutation`
6. `package`
7. `publish artifact`
8. `deploy development`

O deploy para staging e production pode ser condicionado a:

- merge na branch principal;
- criação de tag;
- aprovação manual.

### WP4: Melhoria dos testes

Não tentem simplesmente aumentar o número de testes. Organizem-nos por níveis:

| Nível | Objetivo |
|---|---|
| Unitários | Regras de domínio e value objects |
| Serviço | Regras de negócio e casos limite |
| Repositório | Queries e persistência |
| Controller/API | Status HTTP, validação e segurança |
| Sistema | Fluxos completos através da API |

Prioridade para testar:

- Empréstimo acima do limite permitido.
- Leitor com empréstimo atrasado.
- Devolução e cálculo de multa.
- ISBN, email e números inválidos.
- Autorização por perfil.
- Recursos inexistentes.
- Conflitos de concorrência.
- Upload de fotografias.
- Pesquisas e endpoints estatísticos da Fase 2.

O PIT deve responder a uma pergunta importante:

> Os testes falhariam se introduzíssemos defeitos plausíveis?

Documentem alguns mutantes sobreviventes e adicionem testes para os eliminar.

### WP5: Deployment configurável

Definam pelo menos três ambientes:

| Ambiente | Uso |
|---|---|
| Development | Execução local e testes rápidos |
| Staging | Validação próxima da produção |
| Production | Versão publicada |

Cada ambiente deve ter configuração própria para:

- base de dados;
- porta;
- diretório de uploads;
- logging;
- secrets;
- perfil Spring.

Não coloquem passwords ou chaves no repositório. Usem variáveis de ambiente e ficheiros `.env.example`.

### WP6: Avaliação

No final comparem quantitativamente o antes e o depois:

| Métrica | Antes | Depois |
|---|---:|---:|
| Tempo de feedback | manual | automático |
| Testes executados | 102 | valor final |
| Cobertura | baseline | final |
| Mutation score | inexistente | final |
| Análise estática | inexistente | issues encontradas |
| Deployment | manual | automatizado |
| Reprodutibilidade | baixa | demonstrada |
| Artefactos versionados | não | sim |

A avaliação deve incluir limitações reais, por exemplo:

- PIT é lento.
- H2 não representa totalmente uma base de dados real.
- Testes de integração podem ser instáveis.
- Deployment local não equivale a produção real.
- A API externa pode tornar a pipeline dependente da rede.

## Distribuição para dois estudantes

### Estudante A

- System-as-is.
- Build e Maven.
- CI básica.
- Análise estática.
- Cobertura.
- Relatório de métricas.

### Estudante B

- Estratégia de testes.
- Testes em falta.
- Mutation testing.
- Docker e ambientes.
- Deployment.
- Testes da pipeline.

Ambos devem rever e compreender tudo. A apresentação individual torna perigoso cada pessoa conhecer apenas a sua parte.

## Calendário até 1 de novembro

### 25 setembro a 2 outubro

- Criar board e issues.
- Executar baseline.
- Corrigir o Maven Wrapper.
- Medir testes e cobertura.
- Documentar o processo atual.

### 3 a 10 outubro

- Definir System-to-be.
- Escolher ferramentas.
- Criar pipeline mínima: build + testes.
- Adicionar artefacto JAR.

### 11 a 18 outubro

- Adicionar análise estática, JaCoCo e relatórios.
- Corrigir problemas relevantes.
- Melhorar testes das regras de negócio.

### 19 a 25 outubro

- Adicionar PIT.
- Criar Dockerfile e configuração por ambiente.
- Automatizar deployment para development/staging.

### 26 a 30 outubro

- Executar a pipeline várias vezes.
- Recolher evidências.
- Comparar métricas.
- Fechar documentação.

### 31 outubro

- Congelar código.
- Testar a reprodução numa máquina limpa.
- Preparar demonstração e apresentação individual.

## Critério prático de conclusão

Considerem o projeto concluído apenas quando conseguirem demonstrar, a partir de um commit novo:

1. A pipeline arranca automaticamente.
2. Um erro de compilação faz a pipeline falhar.
3. Um teste falhado bloqueia o artefacto.
4. A cobertura e mutation testing geram relatórios.
5. O JAR ou Docker image fica disponível como artefacto.
6. A aplicação arranca num ambiente configurado.
7. Conseguem explicar as métricas e decisões.
8. Conseguem reproduzir tudo sem passos manuais escondidos.

A documentação arquitetural existente em `arqsoft-documentation.md` pode ser reutilizada como base do System-as-is, mas precisa de ser complementada com o processo de build, testes, qualidade e deployment.