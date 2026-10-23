# ARQSOFT — Documentação Global de Arquitetura (P1)

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

## 3. Síntese do System-as-is (1.3.1)

O detalhe exaustivo de engenharia reversa encontra-se formalmente documentado em:
👉 **[Documentação Completa do System-as-is](./system-as-is/system-as-is.md)**

### Principais Características do Sistema Inicial
* **Paradigma:** Monólito Modular (*Modular Monolith*) orientado a domínios de negócio (DDD).
* **Camadas Internas:** Cada módulo de domínio (`bookmanagement`, `lendingmanagement`, `readermanagement`, `usermanagement`, etc.) adota internamente uma organização em camadas (`api`, `services`, `model`, `infrastructure/repositories`), isolando o núcleo de domínio de detalhes tecnológicos.
* **Vistas Documentadas:**
  * **Vista Arquitetural / Lógica (VA):** 4 níveis C4 detalhando portas de interface pública (*ball-and-socket*), limites de contexto e desacoplamento via DIP.
  * **Vista de Implementação (VI):** Empacotamento Maven JAR, módulos de código Java e centralização técnica da `Persistência` (Spring Data JPA / Hibernate).
  * **Mapeamento de Manifestação:** Relações formais `<<manifest>>` entre a VA e a VI nos Níveis 3 e 4.
  * **Vista Física (VF):** Topologia de nós (`Server localhost`, `Client Workstation`, `Cloud Ninja API`).
  * **Vista de Processos (VP):** Realização sequencial dos 4 cenários base (*Create Lending*, *Register Book*, *Return Lending*, *Top 5 Books Lent*).

---

## 4. Requisitos de Significância Arquitetural (ASR) & Táticas ADD (1.3.1.2)

Em estrito cumprimento do **Tópico 1.3.1.2 do Enunciado (*Requirements, especially ASR*)**, esta secção detalha os requisitos funcionais primários e a especificação formal dos cenários de atributos de qualidade (*Quality Attribute Scenarios*) segundo a metodologia de referência do *Software Engineering Institute* (SEI — Bass, Clements, Kazman).

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
* **Estímulo:** Necessidade de suportar um novo fornecedor de metadados bibliográficos (ex.: WorldCat, Biblioteca Nacional).
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

## 6. Matriz Global de Rastreabilidade e Consistência (1.3.1.4)

A matriz seguinte assegura a rastreabilidade e consistência bidirecional entre requisitos, decisões de desenho, modelos arquiteturais e a solução implementada:

| Requisito / Problema | ASR | Decisão Arquitetural (ADD) | Padrões de Desenho | Artefactos / Módulos de Código | Modelos Visuais Associados |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **1.3.3.1 Fontes Bibliográficas** | ASR01, ASR02, ASR05, ASR06 | Desacoplamento via portas e adaptadores; agregação com fallback; ativação condicional em setup time. | **Adapter**, **Gateway**, **Composite / Chain**, **Canonical DTO** | `BookInfoProvider`, `GoogleBooksAdapter`, `OpenLibraryAdapter`, `CompositeBookInfoProvider`, `BibliographicConfig` | C4 Component (N3) do `bookmanagement`; Diagrama de Sequência de Consulta Externa |
| **1.3.3.2 Notificações a Leitores** | ASR02, ASR03, ASR07 | Publicação orientada a eventos de domínio; despachador multicanal assíncrono; ativação por propriedades. | **Observer / Domain Events**, **Strategy**, **Composite** | `LendingCreatedEvent`, `LendingReturnedEvent`, `NotificationSender`, `CompositeNotificationSender`, `NotificationConfig` | C4 Component (N3) do `readermanagement`; Diagrama de Sequência de Despacho de Eventos |
| **1.3.3.3 Políticas de Empréstimo** | ASR02, ASR04, ASR05 | Encapsulamento de regras variáveis em estratégias polimórficas; resolução dinâmica em runtime; propriedades tipadas. | **Strategy**, **Policy Resolver / Factory**, **Specification** | `LendingPolicy`, `StandardLendingPolicy`, `PremiumReaderLendingPolicy`, `LendingPolicyResolver`, `LendingPolicyProperties` | C4 Class/Design (N4) de `lendingmanagement`; Diagrama de Classes de Políticas |
| **1.3.3.4 Testes Automatizados** | ASR05 | Arquitetura hermética e inversão de controlo para injeção de mocks/stubs em todos os níveis de teste. | **Inversion of Control (IoC)**, **Dependency Injection** | Testes de unidade, Transparent-box de domínio, PITest Mutation, `@SpringBootTest`, `SystemEndToEndTest` | Rastreabilidade formal em `architectural-factors.md` e suítes de teste em `src/test/java` |

---

## 7. Metas de Desenvolvimento e Automação de Testes (1.3.3)

O desenvolvimento das novas funcionalidades (Tópico 1.3.3) e da respetiva verificação automatizada obedece rigorosamente aos seguintes padrões de qualidade:

1. **Informação Bibliográfica (1.3.3.1):**
   * Suporte obrigatório às APIs do **Google Books** e do **Open Library**.
   * Normalização dos modelos de dados díspares num modelo canónico comum.
   * Suporte à seleção por configuração de fontes únicas ou combinadas com mecanismo de fallback resiliente.
2. **Notificações a Leitores (1.3.3.2):**
   * Suporte a envio via **Email**, **SMS** e **Webhook**, operando isoladamente ou em qualquer combinação.
   * Total desacoplamento entre o fluxo de empréstimo e os canais de transmissão através de eventos assíncronos.
3. **Políticas de Empréstimo (1.3.3.3):**
   * Parametrização externa de limiares (prazos, tolerâncias, limites de livros, multas) em ficheiro de configuração sem alterar código.
   * Avaliação dinâmica da política elegível em tempo de execução com base no contexto do pedido.
4. **Bateria de Testes Automatizados (1.3.3.4):**
   * **Opaque-box com SUT = classes:** Testes unitários com partições de equivalência e análise de valores limite (BVA).
   * **Transparent-box com SUT = domain classes:** Cobertura de ramos estruturais ($\ge 70\%$) nas entidades e regras de domínio.
   * **Mutation tests com SUT = classes:** Execução do PITest atingindo taxa de mutantes mortos $\ge 60\%$.
   * **Opaque-box com SUT = controller + service + gateways:** Testes de integração vertical determinísticos e offline com mocks.
   * **Opaque-box com SUT = system:** Teste de sistema de ponta a ponta (*End-to-End*) validando o percurso completo da aplicação.