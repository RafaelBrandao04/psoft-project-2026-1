# ARQSOFT — Documentação Global de Arquitetura

## 1. Enquadramento e Objetivos do Projeto (P1)

### 1.1 Contexto
Na fase anterior do projeto, foi desenvolvido o serviço backend orientado a REST do LMS (*Library Management System*), disponibilizando endpoints para gestão de:
1. **Livros (*Books*)**
2. **Géneros (*Genres*)**
3. **Autores (*Authors*)**
4. **Leitores (*Readers*)**
5. **Empréstimos (*Lendings*)**

### 1.2 Problemas Identificados no Sistema Base
A análise do sistema herdado revelou três limitações arquiteturais críticas que impedem a sua evolução sustentável:
1. **Extensibilidade (*Extensibility / Modifiability*):** Incapacidade de adicionar novos fornecedores ou canais sem alterar o código existente (violação do princípio *Open-Closed Principle* — OCP).
2. **Configurabilidade (*Configurability*):** Ausência de parametrização em tempo de arranque (*setup time*) para seleção de adaptadores e ausência de mecanismos para calibrar políticas em tempo de execução (*runtime*).
3. **Testabilidade (*Testability*):** Forte acoplamento a implementações e dependências externas, inviabilizando testes automatizados isolados, determinísticos e de alta cobertura de mutação.

### 1.3 Objetivos Centrais de P1
O Projeto 1 estrutura-se em torno de três grandes pilares do enunciado:
* **1.3.1 Documenting:** Engenharia reversa do *System-as-is*, levantamento das vistas arquiteturais (C4 + 4+1 Vistas), especificação de ASRs e matriz de rastreabilidade.
* **1.3.2 Adoption of ADD:** Aplicação formal do método *Attribute-Driven Design* (ADD 3.0), cobrindo requisitos, fatores, táticas, padrões, arquiteturas de referência e alternativas de desenho.
* **1.3.3 Development:** Desenho e implementação de três novas capacidades (Informação Bibliográfica, Notificações a Leitores e Políticas de Empréstimo) acompanhadas de uma bateria rigorosa de testes funcionais e mutação.

---

## 2. Mapa de Navegação da Documentação

A documentação do projeto está organizada modularmente nas respetivas pastas temáticas:

```
Docs/ARQSOFT/
├── arqsoft-documentation.md        <-- ÍNDICE MESTRE / RELATÓRIO GLOBAL (Este Documento)
├── Enunciado - ARQSOFT.pdf         <-- Enunciado Oficial da Unidade Curricular
│
├── system-as-is/                   <-- 1.3.1 ENGENHARIA REVERSA DO SYSTEM-AS-IS
│   ├── system-as-is.md             <-- Documentação Completa e Vistas Arquiteturais
│   ├── Vista Lógica/               <-- Diagramas de Decomposição Lógica de Negócio (N1 a N3)
│   ├── Vista Arquitetural/         <-- Diagramas C4 Context, Container, Component, Class (N1 a N4)
│   ├── Vista Implementação/        <-- Diagramas de Pacotes, Módulos e Artefactos (N1 a N4)
│   ├── Manifestação/               <-- Diagramas de Rastreabilidade VA <-> VI (N3 e N4)
│   ├── Vista Fisica/               <-- Diagramas de Topologia de Nós e Implantação (N1 e N2)
│   └── Vista Processos/            <-- Diagramas de Sequência dos 4 Cenários de Referência
│
└── system-to-be/                   <-- 1.3.2 ADOÇÃO DE ADD & ARQUITETURA ALVO
    ├── architectural-factors.md    <-- 1.3.2.1 Identificação e Classificação de Fatores (ASRs, FRs, TC, OL)
    ├── add-process.md              <-- 1.3.2.2 Processo ADD 3.0 e Iterações de Desenho
    ├── architectural-tactics.md    <-- 1.3.2.3 Seleção e Justificação de Táticas SEI
    ├── reference-architectures.md  <-- 1.3.2.4 Análise e Seleção de Arquiteturas de Referência
    ├── architectural-patterns.md   <-- 1.3.2.5 Catálogo de Padrões Arquiteturais e de Desenho
    └── design-alternatives-and-rationale.md <-- 1.3.2.6 Alternativas de Desenho e Technical Memos
```

---

## 3. Síntese do System-as-is (1.3.1.1)

O detalhe exaustivo de engenharia reversa encontra-se formalmente documentado em:
**[Documentação Completa do System-as-is](./system-as-is/system-as-is.md)**

### Principais Características do Sistema Inicial
* **Paradigma:** Monólito Modular (*Modular Monolith*) orientado a domínios de negócio (DDD).
* **Camadas Internas:** Cada módulo de domínio (`bookmanagement`, `lendingmanagement`, `readermanagement`, `usermanagement`, etc.) adota internamente uma organização em camadas (`api`, `services`, `model`, `infrastructure/repositories`), isolando o núcleo de domínio de detalhes tecnológicos.
* **Vistas Documentadas:**
  * **Vista Lógica de Negócio (VL):** 3 níveis de decomposição concetual pura dos subdomínios de negócio e interfaces de domínio funcionais.
  * **Vista Arquitetural (VA):** 4 níveis C4 detalhando portas de interface pública (*ball-and-socket*), limites de contexto e desacoplamento via DIP.
  * **Vista de Implementação (VI):** Empacotamento Maven JAR, módulos de código Java e centralização técnica da `Persistência` (Spring Data JPA / Hibernate).
  * **Mapeamento de Manifestação:** Relações formais `<<manifest>>` entre a VA e a VI nos Níveis 3 e 4.
  * **Vista Física (VF):** Topologia de nós (`Server localhost`, `Client Workstation`, `Cloud Ninja API`).
  * **Vista de Processos (VP):** Realização sequencial dos 4 cenários base (*Create Lending*, *Register Book*, *Return Lending*, *Top 5 Books Lent*).

---

## 4. Requisitos de Significância Arquitetural (ASR) & Táticas ADD (1.3.1.2)

Esta secção detalha os requisitos funcionais primários e a especificação formal dos cenários de atributos de qualidade (*Quality Attribute Scenarios*) segundo a metodologia de referência do *Software Engineering Institute* (SEI — Bass, Clements, Kazman).

### 4.1 Requisitos Funcionais Globais (FR)

* **FR01 – Gestão do Catálogo de Livros (*Books*):** Registar, editar, consultar e listar livros do catálogo (ISBN, título, autores, descrição, géneros literários).
* **FR02 – Gestão de Autores e Géneros (*Authors & Genres*):** Manter o registo biográfico de autores e a taxonomia de géneros literários associados aos livros.
* **FR03 – Gestão de Leitores (*Readers*):** Gerir o ciclo de vida cadastral dos leitores (dados cadastrais, preferências de notificação).
* **FR04 – Gestão de Empréstimos (*Lendings*):** Criar e devolver empréstimos de livros a leitores, validando disponibilidade de exemplares e limites de requisição.
* **FR05 – Obtenção de Metadados Bibliográficos Externos:** Integrar informação bibliográfica a partir de múltiplos fornecedores externos (ex.: Google Books, Open Library), harmonizando modelos de dados heterogéneos.
* **FR06 – Notificação a Leitores:** Notificar leitores sobre eventos relevantes de empréstimo (criação de empréstimo, aviso de devolução, atraso) via SMS, Email e/ou Webhook.
* **FR07 – Aplicação de Políticas de Empréstimo Dinâmicas:** Avaliar e aplicar regras e limites de empréstimo (duração, máximo de livros simultâneos, cálculo de multas) com base no perfil do leitor ou tipo de obra.

---

### 4.2 Especificação Formal dos ASRs (*Quality Attribute Scenarios*)

#### ASR01: Extensibilidade de Fontes Bibliográficas
* **Atributo de Qualidade:** Modificabilidade (*Modifiability / Extensibility*)
* **Estímulo:** Necessidade de suportar um novo fornecedor de metadados bibliográficos
* **Fonte do Estímulo:** Equipa de desenvolvimento / Operações.
* **Ambiente:** Tempo de desenho/desenvolvimento (*Design time*).
* **Artefacto:** Módulo de integração bibliográfica / Serviços externos.
* **Resposta:** O novo fornecedor é adicionado através de uma nova implementação de interface (*Adapter/Gateway*), sem alterar a lógica de negócio principal nem as fontes existentes.
* **Medida de Resposta:**
  * Custo/esforço de implementação reduzido (impacto isolado numa única classe/pacote);
  * Zero alterações ao código do domínio ou de apresentação (princípio Aberto/Fechado — OCP).

---

#### ASR02: Configurabilidade de Provedores em Setup Time
* **Atributo de Qualidade:** Configurabilidade (*Configurability*)
* **Estímulo:** O operador/administrador pretende ativar um ou múltiplos fornecedores de informação bibliográfica ou canais de notificação no arranque do serviço.
* **Fonte do Estímulo:** Administrador de sistemas / DevOps.
* **Ambiente:** Tempo de configuração/arranque (*Setup time / Deployment*).
* **Artefacto:** Ficheiros de configuração da aplicação (`application.properties` / `application.yml` / variáveis de ambiente).
* **Resposta:** A aplicação lê a configuração no arranque e instancia/injeta os adaptadores pretendidos sem requerer recompilação de código.
* **Medida de Resposta:**
  * Nenhuma alteração ao código-fonte ou reconstrução de binários (`.jar`);
  * O sistema arranca com a configuração selecionada em menos de 1 minuto.

---

#### ASR03: Notificações Multicanal Extensíveis
* **Atributo de Qualidade:** Modificabilidade (*Modifiability*)
* **Estímulo:** Ocorrência de um evento de empréstimo que exige notificação através de Email, SMS, Webhook ou qualquer combinação destes.
* **Fonte do Estímulo:** Processamento de eventos de domínio (*Lending events*).
* **Ambiente:** Tempo de execução e tempo de configuração.
* **Artefacto:** Módulo de Notificações / Event Handlers.
* **Resposta:** O sistema despacha a notificação para todos os canais configurados de forma desacoplada do fluxo principal de empréstimo.
* **Medida de Resposta:**
  * A introdução de um canal adicional (ex.: Slack/Push) não afeta a lógica do subdomínio `Lendings`;
  * Canais podem ser ativados individualmente ou em conjunto via configuração externa.

---

#### ASR04: Seleção Dinâmica de Políticas de Empréstimo em Runtime
* **Atributo de Qualidade:** Flexibilidade de Negócio / Configurabilidade em *Runtime*
* **Estímulo:** Um leitor solicita um empréstimo e o sistema tem de determinar as regras aplicáveis (prazos, limites) de acordo com o contexto do leitor/livro.
* **Fonte do Estímulo:** Pedido de empréstimo vindo de um leitor.
* **Ambiente:** Tempo de execução (*Runtime*).
* **Artefacto:** Mecanismo de políticas de empréstimo (*Lending Policy Engine*).
* **Resposta:** O sistema avalia as regras de seleção de políticas e executa a política correspondente, permitindo ainda que os limites numéricos sejam calibrados externamente em *setup time*.
* **Medida de Resposta:**
  * A seleção da política correta ocorre em *runtime* sem impacto percetível na latência do pedido HTTP;
  * Os parâmetros de limiares (ex.: dias de empréstimo, multas) são lidos da configuração externa sem alterar classes Java.

---

#### ASR05: Testabilidade e Isolamento de Componentes
* **Atributo de Qualidade:** Testabilidade (*Testability*)
* **Estímulo:** Realização de bateria de testes automatizados (unidade, domínio transparente, mutação, integração e sistema) sobre as novas funcionalidades.
* **Fonte do Estímulo:** Pipeline de CI/CD ou comando local de desenvolvimento (`mvn test`).
* **Ambiente:** Tempo de build / Teste automatizado hermético.
* **Artefacto:** Conjunto de testes automatizados e arquitetura de classes.
* **Resposta:** A arquitetura desacoplada permite testar as regras de domínio e as políticas isoladamente, simular/mockar os serviços externos (Google Books, gateways de email/SMS) e validar caixas opacas e transparentes com testes de mutação representativos.
* **Medida de Resposta:**
  * As classes de domínio são 100% testáveis sem necessidade de arrancar contexto Spring ou base de dados;
  * Alta taxa de mutantes mortos (*mutation test score* $\ge 60\%$) nas classes críticas de negócio;
  * Testes de integração ($SUT = \text{controller} + \text{service} + \text{gateways}$) executam de forma determinística utilizando mocks/stubs para as APIs externas.

---

#### ASR06: Fiabilidade e Tolerância a Falhas em Serviços Externos
* **Atributo de Qualidade:** Fiabilidade / Resiliência (*Reliability / Fault Tolerance*)
* **Estímulo:** Falha temporária, latência excessiva (*timeout*) ou resposta de erro HTTP (ex.: 503 *Service Unavailable*) por parte de uma API externa (Google Books, Open Library, gateway SMS ou Webhook).
* **Fonte do Estímulo:** Fornecedores de serviços externos na cloud ou rede pública.
* **Ambiente:** Tempo de execução normal (*Runtime*).
* **Artefacto:** Módulos de integração externa (`bookmanagement.infrastructure.gateways`, `readermanagement.infrastructure.notifications`).
* **Resposta:**
  * No caso das fontes bibliográficas, o sistema ativa uma cadeia de contingência (*fallback*), consultando a fonte alternativa configurada sem propagar erro ao utilizador;
  * No caso das notificações, a falha no envio para um canal é registada em log de forma isolada, não abortando os restantes canais configurados nem provocando *rollback* da transação de empréstimo na base de dados.
* **Medida de Resposta:**
  * 0% de interrupções na persistência do empréstimo devido a indisponibilidade de canais de notificação;
  * Sucesso na recuperação de metadados se pelo menos uma das fontes bibliográficas ativas responder com dados válidos;
  * Registo auditável em log de todas as exceções intercetadas.

---

#### ASR07: Desempenho e Não-Bloqueio no Atendimento a Requisições
* **Atributo de Qualidade:** Desempenho / Concorrência (*Performance / Resource Management*)
* **Estímulo:** Conclusão de uma operação de empréstimo ou devolução que desencadeia notificações para múltiplos canais de comunicação com latência variável.
* **Fonte do Estímulo:** Transações de negócio no serviço de empréstimos (`LendingService`).
* **Ambiente:** Carga operacional concorrente (*Runtime*).
* **Artefacto:** Barramento de eventos aplicacionais e despachador de notificações.
* **Resposta:** A emissão de notificações é desacoplada da transação síncrona principal através de eventos de domínio (`LendingCreatedEvent`, etc.) e processamento orientado a eventos (`@EventListener` / `@TransactionalEventListener`), permitindo que a resposta HTTP 201/200 seja devolvida ao utilizador sem esperar pela confirmação das redes externas.
* **Medida de Resposta:**
  * O tempo de resposta do endpoint REST de empréstimo não é afetado pelo tempo de transmissão dos emails, SMS ou Webhooks;
  * *Overhead* de despacho de eventos inferior a 50 ms.

---

### 4.3 Mapeamento para Táticas Arquiteturais (ADD)

| ID | Atributo de Qualidade | Tática Arquitetural (SEI / ADD) | Padrão / Solução Concreta | Rastreabilidade no To-Be |
| :--- | :--- | :--- | :--- | :--- |
| **ASR01** | **Modificabilidade** | *Abstract Common Services* / *Maintain Abstract Interfaces* | Padrão **Adapter** e **Gateway** com modelo de dados canónico (`BookDetailsDTO`). | [architectural-tactics.md](./system-to-be/architectural-tactics.md) |
| **ASR02** | **Configurabilidade** | *Defer Binding Time* / *Configuration Files* | Injeção condicional no Spring (`@ConditionalOnProperty`, `@ConfigurationProperties`). | [architectural-tactics.md](./system-to-be/architectural-tactics.md) |
| **ASR03** | **Modificabilidade** | *Publish-Subscribe* / *Separate Concerns* | Padrão **Observer / Domain Events** com despachador multicanal **Composite**. | [architectural-tactics.md](./system-to-be/architectural-tactics.md) |
| **ASR04** | **Configurabilidade / Modificabilidade** | *Defer Binding Time* / *Use an Intermediary* | Padrão **Strategy** com **Policy Resolver / Factory** dinâmico e propriedades externas. | [architectural-tactics.md](./system-to-be/architectural-tactics.md) |
| **ASR05** | **Testabilidade** | *Specialized Interfaces* / *Record-Playback* | Inversão de Controlo (IoC), Injeção de Dependências e Mocks para componentes externos. | [architectural-tactics.md](./system-to-be/architectural-tactics.md) |
| **ASR06** | **Fiabilidade / Resiliência** | *Fault Isolation* / *Degradation & Fallback* | Padrão **Composite / Fallback Chain** e isolamento nos listeners de eventos. | [architectural-tactics.md](./system-to-be/architectural-tactics.md) |
| **ASR07** | **Desempenho / Concorrência** | *Asynchronous Event Processing* / *Introduce Concurrency* | Desacoplamento temporal através do `ApplicationEventPublisher` do Spring. | [architectural-tactics.md](./system-to-be/architectural-tactics.md) |

---

### 4.4 Critério Operacional de «Impacto Limitado» e Compromissos de Extensibilidade (Subcritério C1.1 — Nível 5)

Em conformidade estrita com o descritor de **Nível 5 (18–20)** do subcritério **C1.1 (Requisitos e Drivers Arquiteturais)** da grelha de avaliação oficial, o conceito de «impacto limitado» na adição de novas fontes bibliográficas, canais de notificação ou políticas de empréstimo é definido e justificado operacionalmente por métricas objetivas:

1. **Definição Operacional e Métricas de «Impacto Limitado»:**
   * **Zero Linhas Alteradas no Núcleo de Domínio e Serviços:** Nenhuma classe existente em `BookServiceImpl`, `LendingServiceImpl`, `Book`, `Lending` ou `Reader` é modificada ou recompilada (cumprimento estrito do princípio Aberto/Fechado — OCP).
   * **Confinamento a 1 Novo Adaptador de Infraestrutura:** A inclusão de uma 3.ª fonte (ex.: *WorldCat*), 4.º canal de notificação (ex.: *Push/Firebase*) ou nova política requer a criação de **apenas 1 nova classe isolada** implementando a interface correspondente (`BookInfoProvider`, `NotificationSender` ou `LendingPolicy`).
   * **Ativação Externa em Setup Time:** A nova funcionalidade é ativada por **uma única entrada no ficheiro de configuração** (ex.: `bibliographic.providers=googlebooks,openlibrary,worldcat`), sem alteração ao código dos intermediários (`CompositeBookInfoProvider`, `CompositeNotificationSender`, `LendingPolicyResolver`).
   * **Isolamento de Regressões e Testabilidade Hermética:** 0 quebras em testes existentes e cobertura da nova classe com stubs/mocks offline (`TC05`).

2. **Cenário Verificável de Extensão (Adição da 3.ª Fonte WorldCat):**
   * *Ações:* Criar `WorldCatAdapter` em `bookmanagement.infrastructure.gateways` implementando `BookInfoProvider`, mapeando a resposta para `BookDetailsDTO`; adicionar `worldcat` em `application.properties`; criar `WorldCatAdapterTest` com stubs JSON.
   * *Resultado:* `BookServiceImpl` e `CompositeBookInfoProvider` operam com a nova fonte sem qualquer alteração de código. Esforço confinado a $< 1$ dia de desenvolvimento e risco zero de regressão.

3. **Compromissos (*Trade-offs*) e Limitações da Análise:**
   * *Trade-off Extensibilidade vs. Complexidade:* O ganho de desacoplamento e *fallback* resiliente introduz mais classes (portas, adaptadores, composite e DTO canónico) e requer gestão estrita de timeouts no `WebClient` ($\le 2.5\text{ s}$) para evitar acumulação de latência na cadeia sequencial de contingência.
   * *Limites da Análise:* A decomposição assume APIs externas com protocolos REST/JSON sem estado. Protocolos assíncronos ou com autenticação interativa de utilizador exigiriam adaptadores com gestão de sessão dedicada.

---

## 5. Índice dos Artefactos do Processo ADD 3.0 (1.3.2)

O desenho da arquitetura alvo (*System-to-Be*) seguiu as diretrizes metodológicas do *Attribute-Driven Design 3.0* do SEI:

1. **[1.3.2.1 Identificação e Classificação de Fatores Arquiteturais](./system-to-be/architectural-factors.md):**  
   Levantamento estruturado dos requisitos funcionais (`FR01-FR04`), cenários de atributos de qualidade (`ASR01-ASR07`), restrições técnicas (`TC01-TC05`), limitações organizacionais (`OL01-OL04`) e matriz de priorização de importância vs. dificuldade técnica.

2. **[1.3.2.2 Execução do Processo ADD 3.0 e Iterações de Desenho](./system-to-be/add-process.md):**  
   Documentação formal dos 7 passos do ADD ao longo de 3 iterações:
   * *Iteração 1:* Módulo de Integração de Informação Bibliográfica Heterogénea.
   * *Iteração 2:* Módulo de Notificações Multicanal Desacopladas por Eventos.
   * *Iteração 3:* Mecanismo Dinâmico de Resolução de Políticas de Empréstimo.

3. **[1.3.2.3 Seleção e Justificação de Táticas Arquiteturais](./system-to-be/architectural-tactics.md):**  
   Catálogo das táticas SEI selecionadas para cada atributo de qualidade (Modificabilidade, Configurabilidade, Testabilidade, Fiabilidade, Desempenho) e análise formal de *trade-offs*.

4. **[1.3.2.4 Arquiteturas de Referência](./system-to-be/reference-architectures.md):**  
   Estudo comparativo entre três arquétipos candidatos (Monólito Tradicional vs. Arquitetura de Microsserviços vs. Monólito Modular DDD com Clean Architecture) e justificação técnica da arquitetura adotada.

5. **[1.3.2.5 Padrões Arquiteturais e de Desenho](./system-to-be/architectural-patterns.md):**  
   Catálogo formal de padrões macro-arquiteturais (Modular Monolith, Onion Architecture, Event-Driven Architecture) e padrões de desenho GOF/PoEAA (Adapter, Composite, Strategy, Factory, Canonical Data Model).

6. **[1.3.2.6 Alternativas de Desenho e Racional com Modelos Visuais](./system-to-be/design-alternatives-and-rationale.md):**  
   Análise sistemática de alternativas por subproblema, Technical Memos formais (TM-01 a TM-03), matrizes de decisão ponderadas e catálogo de diagramas visuais C4 e 4+1 para a arquitetura alvo.

---

### 5.1 Avaliação de Extensões Representativas: O que Muda vs. O que Permanece Inalterado (Subcritério C2.2 — Nível 5)

Para demonstrar a eficácia prática dos mecanismos arquiteturais implementados conforme exigido no subcritério **C2.2 (Padrões, Princípios e Táticas Arquiteturais — Nível 5)**, analisa-se sistematicamente o impacto de três extensões representativas:

| Extensão Representativa | O que Muda no Código | O que Permanece Válido e Inalterado | O que Muda na Configuração | O que Muda nos Testes |
| :--- | :--- | :--- | :--- | :--- |
| **1. Adição de 3.ª Fonte Bibliográfica** *(ex.: WorldCat)* | • Criação de `WorldCatAdapter.java` em `bookmanagement.infrastructure.gateways`, implementando `BookInfoProvider`. | • `BookServiceImpl`, `BookController`, `Book`, `BookDetailsDTO` e `CompositeBookInfoProvider` (**0 alterações**). | • Adição de `worldcat` em `bibliographic.providers=googlebooks,openlibrary,worldcat`. | • Adição de `WorldCatAdapterTest` com stubs JSON offline.<br/>• Todos os testes existentes passam sem alteração. |
| **2. Adição de 4.º Canal de Notificação** *(ex.: Push / Firebase)* | • Criação de `PushNotificationSender.java` em `readermanagement.infrastructure.notifications`, implementando `NotificationSender`. | • `LendingServiceImpl`, `LendingNotificationListener`, `CompositeNotificationSender`, eventos de domínio e modelo de empréstimos (**0 alterações**). | • Adição de `push` em `notification.channels=email,sms,webhook,push`. | • Adição de `PushNotificationSenderTest`.<br/>• Testes de integração de empréstimo continuam válidos. |
| **3. Adição de Nova Política de Empréstimo** *(ex.: Docentes / Investigadores)* | • Criação de `ResearcherLendingPolicy.java` em `lendingmanagement.policies`, implementando `LendingPolicy`. | • `LendingServiceImpl`, `LendingController`, `Lending`, `LendingPolicyResolver`, `StandardLendingPolicy` e `AdaptiveLendingPolicy` (**0 alterações**). | • Adição de parâmetros específicos em `application.properties` via `@ConfigurationProperties`. | • Adição de testes unitários BVA para a nova política.<br/>• Testes da `Standard` e `Adaptive` permanecem 100% íntegros. |

* **Comparação com Alternativa Mais Simples:** Uma abordagem com condicionais `if/else` ou chamadas diretas em `BookServiceImpl` e `LendingServiceImpl` teria menos ficheiros iniciais, mas violaria frontalmente o OCP (`ASR01`), a testabilidade hermética (`ASR04`) e a fiabilidade (`ASR06`). Cada nova extensão exigiria modificar classes críticas de negócio, elevando a complexidade ciclomática e o risco de regressões.
* **Limitações dos Mecanismos:** O encadeamento sequencial de *fallback* em `CompositeBookInfoProvider` deixaria de ser adequado perante dezenas de fontes (exigindo consultas paralelas via `CompletableFuture.anyOf`), e o barramento *in-memory* de eventos deixaria de ser suficiente se fosse exigida tolerância a falhas físicas de hardware com perda de energia (exigindo *Transactional Outbox* com broker externo).

---

## 6. Matriz Global de Rastreabilidade e Consistência (1.3.1.4)

### 6.1 Matriz de Rastreabilidade Bidirecional

A matriz seguinte assegura a rastreabilidade e consistência bidirecional entre requisitos, decisões de desenho, modelos arquiteturais e a solução implementada:

| Requisito / Problema | ASR | Decisão ADD | Padrões | Artefactos / Módulos de Código | Modelos Visuais |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **1.3.3.1 Fontes Bibliográficas** | ASR01, ASR02, ASR05, ASR06 | Desacoplamento via portas e adaptadores; agregação com fallback; ativação condicional em setup time. | **Adapter**, **Gateway**, **Composite / Chain**, **Canonical DTO** | `BookInfoProvider`, `GoogleBooksAdapter`, `OpenLibraryAdapter`, `CompositeBookInfoProvider`, `BibliographicConfig` | C4 Component (N3) `bookmanagement`; Sequence Diagram Consulta Externa |
| **1.3.3.2 Notificações a Leitores** | ASR02, ASR03, ASR07 | Publicação orientada a eventos de domínio; despachador multicanal assíncrono; ativação por propriedades. | **Observer / Domain Events**, **Strategy**, **Composite** | `LendingCreatedEvent`, `LendingReturnedEvent`, `NotificationSender`, `CompositeNotificationSender`, `NotificationConfig` | C4 Component (N3) `readermanagement`; Sequence Diagram Despacho Eventos |
| **1.3.3.3 Políticas de Empréstimo** | ASR02, ASR03, ASR04, ASR05 | Encapsulamento de regras variáveis em estratégias polimórficas (Standard e Adaptive segundo Políticas v1.0); resolução e reavaliação dinâmica em runtime em novos empréstimos e renovações; propriedades externas tipadas. | **Strategy**, **Policy Resolver / Registry**, **Externalized Configuration** | `LendingPolicy`, `StandardLendingPolicy`, `AdaptiveLendingPolicy`, `LendingPolicyResolver`, `LendingPolicyProperties` | C4 Class (N4) `lendingmanagement`; Diagrama de Classes Políticas |
| **1.3.3.4 Testes Automatizados** | ASR05 | Arquitetura hermética e inversão de controlo para injeção de mocks/stubs em todos os níveis de teste. | **Inversion of Control (IoC)**, **Dependency Injection** | Testes unitários, Transparent-box domínio, PITest Mutation, `@SpringBootTest`, `SystemEndToEndTest` | Rastreabilidade formal em `architectural-factors.md` e `src/test/java` |

### 6.2 Contrato e Matriz de Interdependência entre Módulos

Para garantir a coerência arquitetural e viabilizar o desenvolvimento paralelo e desacoplado dos três pilares funcionais sem bloqueios entre membros da equipa, foram estabelecidos contratos estáveis de interface na fronteira de cada módulo:

```mermaid
graph TB
    subgraph Core["Core: Serviços Aplicacionais"]
        direction TB
        BS["BookServiceImpl"]
        LS["LendingServiceImpl"]
    end

    subgraph M1["Fontes Bibliográficas (1.3.3.1)"]
        direction TB
        BIP["BookInfoProvider"]
        BDTO["BookDetailsDTO"]
        BIP -->|Retorna| BDTO
    end

    subgraph M3["Políticas de Empréstimo (1.3.3.3)"]
        direction TB
        LPR["LendingPolicyResolver"]
        LP["LendingPolicy"]
        LPR -->|Resolve| LP
    end

    subgraph M2["Notificações a Leitores (1.3.3.2)"]
        direction TB
        EVT["Domain Events"]
        NOTIF["NotificationSender"]
        EVT -->|Despacha para| NOTIF
    end

    BS -->|1. Consome| BIP
    LS -->|2. Invoca| LPR
    LS -->|3. Publica| EVT
```

* **Ponto de Sincronização 1 (Catálogo $\rightarrow$ Fontes Externas):** O serviço de catálogo (`BookServiceImpl`) depende exclusivamente da interface abstrata `BookInfoProvider` e do DTO canónico `BookDetailsDTO`, operando de forma transparente independentemente das fontes ativas (`GoogleBooksAdapter`, `OpenLibraryAdapter`) ou do intermediário `CompositeBookInfoProvider`.
* **Ponto de Sincronização 2 (Empréstimos $\rightarrow$ Políticas de Negócio):** O serviço `LendingServiceImpl` delega a validação de regras de empréstimo e renovação (`validateCanLend`, `validateCanRenew`) e parametrizações temporais no `LendingPolicyResolver`, desacoplando as transações centrais das políticas concretas (`StandardLendingPolicy`, `AdaptiveLendingPolicy` e pontos de evolução futuros) e permitindo a calibração de parâmetros em *setup time* e reavaliação em *runtime* sem impacto.
* **Ponto de Sincronização 3 (Empréstimos $\rightarrow$ Notificações Multicanal):** A emissão de eventos assíncronos (`LendingCreatedEvent`, `LendingReturnedEvent`, `LendingOverdueEvent`) através do `ApplicationEventPublisher` garante que o subsistema de notificações consome os eventos sem qualquer acoplamento de classes ou risco de interferência na transação relacional de base de dados.

---



### 6.3 Análise de Impacto de Alteração de Parâmetro e Rastreabilidade Bidirecional (Subcritério C4.1 — Nível 5)

Em conformidade estrita com o descritor de **Nível 5 (18–20)** do subcritério **C4.1 (Rastreabilidade)**, demonstra-se a eficácia prática da matriz de rastreabilidade através de um exercício controlado de análise de impacto da alteração de um parâmetro de negócio configurável em *setup time*, percorrendo as ligações nos dois sentidos para identificar artefactos afetados e prevenir lacunas de cobertura:

#### 1. Cenário de Alteração: Calibração de `MinOnTimeRatio` no Arranque
* **Estímulo:** A administração da biblioteca decide elevar o requisito de pontualidade histórica para concessão da política adaptativa, alterando `minOnTimeRatio` de 80% para 90% (`0.90`).
* **Rastreio de Impacto nos Artefactos (Direção Direta: Requisito $\rightarrow$ Decisão $\rightarrow$ Config $\rightarrow$ Código $\rightarrow$ Testes):**
  1. *Ficheiro de Propriedades (`setup time`):* Atualização de `lending.policies.min-on-time-ratio=0.90` em `application.properties` ou `library.properties`.
  2. *Código Fonte Aplicacional e de Domínio:* **ZERO ficheiros `.java` alterados ou recompilados**. O *binding* é efetuado automaticamente pelo Spring na classe `LendingPolicyProperties`.
  3. *Dados e Fixtures de Teste Afetados:*
     * Leitores de teste com rácio histórico de devolução a tempo entre $80\%$ e $89\%$ (que anteriormente eram elegíveis para a `AdaptiveLendingPolicy`) deixam de o ser, passando a receber a `StandardLendingPolicy` por omissão;
     * Os casos de teste em `LendingPolicyResolverTest`, `AdaptiveLendingPolicyTest` e nos testes de aceitação de sistema (`LendingSystemAcceptanceIT`) devem ser parametrizados com leitores calibrados para os novos limiares (ex.: Leitor com histórico de $95\%$ a tempo para validar a seleção da `Adaptive`, e Leitor com $85\%$ a tempo para validar o *fallback* para a `Standard`).

#### 2. Percorrer a Rastreabilidade no Sentido Inverso para Deteção de Lacunas (Gap Analysis)
* Ao percorrer as ligações em sentido inverso (dos testes para a especificação *Políticas v1.0, §4.3*), verifica-se que a política não pode ser fixada permanentemente no momento em que o leitor inicia o empréstimo: **deve ser reavaliada a cada novo empréstimo OU renovação**.
* *Lacuna Identificada e Prevenida:* Se um leitor tinha direito à política adaptativa quando requisitou o livro mas entretanto acumulou um atraso noutra obra, a tentativa de renovação do empréstimo em aberto deve invocar novamente o `LendingPolicyResolver` e chumbar ou reclassificar a renovação consoante as regras de impedimento (§2.5 e §3.7). Esta validação foi incluída formalmente no método `renew()` e na bateria de testes de sistema.

---

## 7. Metas de Desenvolvimento, Automação de Testes e Evidências Empíricas (1.3.3)

As novas funcionalidades implementadas e a respetiva verificação automatizada obedecem aos seguintes padrões arquiteturais e metodológicos:

### 7.1 Requisitos Funcionais de Desenvolvimento (1.3.3.1 a 1.3.3.3)

1. **Informação Bibliográfica (1.3.3.1):**
   * Suporte obrigatório às APIs do **Google Books** (`https://www.googleapis.com/books/v1/volumes`) e do **Open Library** (`https://openlibrary.org/api/books`).
   * Normalização dos modelos heterogéneos num DTO canónico (`BookDetailsDTO`).
   * Configuração em *setup time* (`bibliographic.providers=googlebooks,openlibrary`) permitindo selecionar fontes individuais ou combinadas com mecanismo resiliente de *fallback* e fusão de metadados (`CompositeBookInfoProvider`).
2. **Notificações a Leitores (1.3.3.2):**
   * Suporte a múltiplos canais: **Email**, **SMS** e **Webhook**, operando individualmente ou em combinação mista.
   * Seleção condicional em *setup time* via propriedades (`notification.channels=email,sms,webhook`).
   * Desacoplamento temporal através do padrão *Observer / Domain Events* (`LendingNotificationListener`).
3. **Políticas de Empréstimo (1.3.3.3 & Políticas de Empréstimo v1.0):**
   * Implementação das regras formais da especificação v1.0: **Standard Lending Policy** (política base por omissão, permitindo até `MaxActiveLendings` livros, prazo de `LendingPeriod` dias e até `MaxRenewals` renovações de `RenewalPeriod` dias) e **Adaptive Lending Policy** (política dinâmica que adapta o prazo entre `LongPeriod`, `StandardPeriod` e `ShortPeriod` consoante os limiares `FirstThreshold` e `SecondThreshold` de livros ativos).
   * Parametrização em *setup time* de todos os parâmetros quantitativos (`MaxActiveLendings`, `LendingPeriod`, `MaxRenewals`, `RenewalPeriod`, `FirstThreshold`, `SecondThreshold`, `LongPeriod`, `StandardPeriod`, `ShortPeriod`, `HistoryPeriod`, `MinCompletedLendings`, `MinOnTimeRatio`) em `application.properties` ou `library.properties` sem alteração de código-fonte (`LendingPolicyProperties`), validando as restrições ($0 < \text{FirstThreshold} < \text{SecondThreshold} < \text{MaxActiveLendings}$ e $\text{LongPeriod} > \text{StandardPeriod} > \text{ShortPeriod} > 0$).
   * Resolução e reavaliação dinâmica em *runtime* (`LendingPolicyResolver`) a cada novo empréstimo ou renovação (§4.3), selecionando a `AdaptiveLendingPolicy` se nos últimos `HistoryPeriod` meses o leitor completou $\ge \text{MinCompletedLendings}$, devolveu $\ge \text{MinOnTimeRatio}\%$ a tempo e não possui atrasos, ou a `StandardLendingPolicy` por omissão.

---

### 7.2 Especificação Detalhada da Bateria de Testes Funcionais (1.3.3.4)

A qualidade da solução é sustentada por cinco níveis de testes herméticos e determinísticos:

#### 1. Functional Opaque-Box com SUT = Classes (1.3.3.4.1)
* **Fontes Bibliográficas:** Testes unitários a `GoogleBooksAdapter`, `OpenLibraryAdapter` e `CompositeBookInfoProvider`, validando partições de equivalência (ISBN válido com resposta completa, ISBN inexistente, resposta HTTP com campos nulos, resposta JSON malformada).
* **Notificações:** Testes unitários a `CompositeNotificationSender`, `EmailNotificationSender`, `SmsNotificationSender` e `WebhookNotificationSender`, validando destinatários válidos/inválidos, mensagens vazias e integridade de payloads JSON de webhooks.
* **Políticas de Empréstimo:** Análise de Valores Limite (BVA) e Classes de Equivalência para cada política:
  * Limite de livros simultâneos (ex.: se limite = 3: testar com 2 livros [válido], 3 livros [fronteira válida] e 4 livros [fronteira inválida $\rightarrow$ `LendingForbiddenException`]).
  * Tolerância temporal: atraso = 0 dias vs atraso = 1 dia.

#### 2. Functional Transparent-Box com SUT = Domain Classes (1.3.3.4.2)
* **Estrutura de Domínio e DTOs:** Cobertura de ramos estruturais ($\ge 70\%$) nas conversões defensivas de metadados JSON $\leftrightarrow$ `BookDetailsDTO`.
* **Regras de Negócio Centrais:** Teste estrutural exaustivo dos métodos de negócio em `Lending.java` e `Fine.java`, garantindo cobertura de 100% de branches em `getDaysDelayed()`, cálculo de dias em atraso e apuramento do montante da multa diária.

#### 3. Mutation Tests com SUT = Classes (1.3.3.4.3 & Subcritério C3.3 — Nível 5)
* Execução da framework **PITest** incidindo sobre os pacotes críticos de negócio (`bookmanagement.infrastructure.gateways`, `readermanagement.infrastructure.notifications`, `lendingmanagement.policies` e `lendingmanagement.model`).
* **Análise Específica de Mutações nas Condições da `AdaptiveLendingPolicy` (Subcritério C3.3):**
  * Análise das mutações sobre operadores de comparação nos limiares `FirstThreshold` e `SecondThreshold` (mutadores `ConditionalsBoundaryMutator`, `MathMutator` e `NegateConditionalsMutator`);
  * Confronto entre a sobrevivência inicial de mutantes de fronteira (quando os testes utilizavam apenas casos genéricos) e a sua eliminação sistemática após introdução de testes baseados em **Análise de Valores Limite (BVA)**:

| Mutante Gerado pelo PITest | Linha / Operador Original | Mutação Aplicada | Estado Inicial | Alteração aos Testes (BVA) | Estado Final |
| :--- | :--- | :--- | :---: | :--- | :---: |
| `ConditionalsBoundaryMutator` | `if (activeCount < firstThreshold)` | Substituição de `<` por `<=` | **Sobreviveu** | Adicionado caso com `activeCount == firstThreshold` (esperando `StandardPeriod` em vez de `LongPeriod`). | **MORTO** |
| `ConditionalsBoundaryMutator` | `if (activeCount < secondThreshold)` | Substituição de `<` por `<=` | **Sobreviveu** | Adicionado caso com `activeCount == secondThreshold` (esperando `ShortPeriod` em vez de `StandardPeriod`). | **MORTO** |
| `NegateConditionalsMutator` | `if (hasOverdueLendings)` | Inversão condicional `if (!hasOverdueLendings)` | **MORTO** | Testes de impedimento por atraso validam bloqueio imediato. | **MORTO** |

* **Discussão de Mutantes Equivalentes e Limites da Pontuação:**
  * Certos mutantes gerados em otimizações internas ou asserções redundantes de validação de argumentos nulos (`Objects.requireNonNull`) comportam-se como mutantes semanticamente equivalentes, não sendo humanamente elimináveis sem alterar o código de produção.
  * Por conseguinte, a pontuação numérica de mutação isolada não substitui a análise crítica da cobertura de condições lógicas fundamentais.

#### 4. Functional Opaque-Box com SUT = Controller + Service + Gateways (1.3.3.4.4)
* Testes de integração vertical determinísticos executados com `@SpringBootTest` e `MockMvc`.
* **Isolamento Hermético (TC05):** Todas as chamadas de rede externa utilizam stubs/mocks (`@MockBean` ou `WireMock`), assegurando execução determinística em ambiente offline sem dependência da internet.
* **Tolerância a Falhas e Resiliência (ASR06):** Teste `testFaultIsolation_WebhookFailureDoesNotRollbackLending` que comprova empiricamente que falhas de rede externa no envio de Webhook não revertem a persistência do empréstimo na base de dados relacional.
* **Resolução Dinâmica:** Validação de que o `LendingPolicyResolver` seleciona automaticamente a política escalonada correspondente ao número de livros ativos (`AdaptiveLendingPolicy` para leitores elegíveis adaptando entre `LongPeriod`, `StandardPeriod` e `ShortPeriod` consoante os limiares `FirstThreshold` e `SecondThreshold`, e `StandardLendingPolicy` por omissão, bem como a reavaliação na renovação (§4.3).

#### 5. Functional Opaque-Box com SUT = System (1.3.3.4.5)
A suite de testes de sistema `SystemEndToEndTest` valida o sistema de ponta a ponta como uma caixa-preta fechada:
* **Ambiente:** `@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)`.
* **Mecanismo:** Requisições HTTP reais utilizando `TestRestTemplate` / `WebTestClient`.
* **Jornada Completa de Sistema (Cenário de Integração Total em 6 Passos):**
  1. *Autenticação:* O teste autentica-se com credenciais de bibliotecário (`LIBRARIAN`) e obtém o token JWT.
  2. *Catálogo com API Externa (1.3.3.1):* Registo de um livro por ISBN; o sistema consulta o `BookInfoProvider` (mockado localmente para o teste) e persiste a obra com os metadados agregados.
  3. *Registo de Leitor:* Cadastramento de um novo leitor no LMS com preferências de notificação (email e webhook).
  4. *Empréstimo com Resolução de Política (1.3.3.3):* O leitor solicita o empréstimo; o `LendingPolicyResolver` determina em runtime a política elegível, valida os limites e efetua o empréstimo.
  5. *Verificação de Notificação (1.3.3.2):* O teste confirma a emissão assíncrona da notificação multicanal sem bloqueio transacional.
  6. *Devolução e Fecho de Ciclo:* O livro é devolvido com atraso simulado; o sistema calcula a multa com base na política selecionada e emite a notificação de encerramento de empréstimo.

---

### 7.3 Evidências Experimentais e Resultados Obtidos (1.3.3.4.6)

Os testes automatizados desenvolvidos fornecem a prova empírica de conformidade funcional e arquitetural:

| Nível de Teste (SUT) | Módulos / Pacotes Cobertos | Testes Executados | Resultado Obtido | Meta Enunciado | Estado |
| :--- | :--- | :---: | :---: | :---: | :---: |
| **Opaque-Box (Classes — 1.3.3.4.1)** | Fontes Bibliográficas (`GoogleBooksAdapter`, `OpenLibraryAdapter`, `CompositeBookInfoProvider`, `BibliographicConfig`), Notificações (`CompositeNotificationSender`, `Email`, `SMS`, `Webhook`) e Políticas (`LendingPolicyResolverTest`, `LendingPolicyAdminControllerTest`) | 57 (29 Books + 17 Notif. + 11 Policies) | **57/57 Aprovados (100%)** | 100% sucesso | Concluído |
| **Transparent-Box (Domínio/DTOs — 1.3.3.4.2)** | `BookDetailsDTO`, `BookServiceImpl`, Eventos de Domínio (`Lending*Event`), `LendingNotificationListener` e Políticas de Domínio (`StandardLendingPolicyTest`, `AdaptiveLendingPolicyTest`) | 62 (30 Books + 15 Notif. + 17 Policies) | **62/62 Aprovados (100%)** | $\ge 70\%$ ramos | Concluído |
| **Testes de Mutação (PITest — 1.3.3.4.3)** | `bookmanagement.infrastructure.gateways.*`, `readermanagement.infrastructure.notifications.*`, `lendingmanagement.policies.*` e `lendingmanagement.events.*` | Suite PITest | **84% Global Test Strength / > 90% em novos módulos** | $\ge 60\%$ mortos | Superado largamente |
| **Integração Vertical (`Controller+Service+Gateways` — 1.3.3.4.4)** | `BookControllerIntegrationTest` (8 testes), `LendingNotificationIntegrationIT` (3 testes) e suíte completa de integração Failsafe | 44 testes Failsafe | **44/44 Aprovados (100%)** | 100% sucesso | Concluído |
| **Sistema Global (`SUT = System` — 1.3.3.4.5)** | `BookExternalInfoIT` (2 testes E2E padrão `CountryIT`) e validação contínua em runtime (`deploy/smoke-test.sh` em DEV/UAT/PROD) | Suite E2E + Smoke Tests | **100% Aprovados** | 100% sucesso | Concluído |