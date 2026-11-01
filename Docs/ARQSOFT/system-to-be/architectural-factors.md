# ARQSOFT — Identificação e Classificação de Requisitos e Fatores Arquiteturais (System-to-Be)

## 1. Enquadramento (ADD 3.0 — Passo 1: Review Inputs)

No método **Attribute-Driven Design (ADD 3.0)**, a arquitetura do *System-to-Be* é orientada por condutores arquiteturais (*Architectural Drivers*) estruturados em:
1. **Requisitos Funcionais com Significância Arquitetural (`FR01`–`FR04`):** Funcionalidades da Secção 1.3.3 do Enunciado e da Especificação de Políticas de Empréstimo v1.0 que moldam os contratos e a decomposição modular.
2. **Requisitos de Atributos de Qualidade (`ASR01`–`ASR07`):** Especificados através de cenários de atributos de qualidade de 6 partes (Fonte, Estímulo, Ambiente, Artefacto, Resposta, Medida da Resposta), respondendo aos problemas de **Extensibilidade**, **Configurabilidade** e **Testabilidade** (Secção 1.2 do Enunciado).
3. **Restrições Técnicas (`TC01`–`TC05`) e Limitações Organizacionais (`OL01`–`OL04`):** Condições tecnológicas e operacionais que delimitam o espaço de soluções.

---

## 2. Identificação e Classificação de Requisitos (*Requirements*)

### 2.1. Requisitos Funcionais Primários (`FR` — Secção 1.3.3)

* **FR01 — Recuperação de Informação Bibliográfica Heterogénea (1.3.3.1):**
  * Obter informação bibliográfica a partir de múltiplos sistemas externos heterogéneos, incluindo **Google Books API**, **Open Library API** e **CrossRef API** (terceira fonte implementada para demonstrar extensibilidade).
  * Suportar seleção em *setup time* (`bibliographic.providers`), contingência (*fallback*), agregação de subconjuntos de metadados (fusão campo-a-campo em `BookDetailsDTO`) e adição de novas fontes com impacto limitado.
* **FR02 — Notificação a Leitores sobre Eventos de Empréstimo (1.3.3.2):**
  * Notificar os leitores acerca de eventos do ciclo de vida dos empréstimos (`LendingCreatedEvent`, `LendingReturnedEvent`, `LendingOverdueEvent`), suportando múltiplos canais (**Email**, **SMS**, **Webhook**) selecionáveis em *setup time* (`notification.channels`), formatação específica por canal e isolamento de falhas por canal.
* **FR03 — Políticas de Empréstimo e Renovação Parametrizáveis (1.3.3.3 & Especificação v1.0 Secções 1 a 5):**
  * Suportar e parametrizar em *setup time* a **`StandardLendingPolicy`** (Secção 2: `MaxActiveLendings`, `LendingPeriod`, `MaxRenewals`, `RenewalPeriod`) e a **`AdaptiveLendingPolicy`** (Secção 3: `MaxActiveLendings`, `FirstThreshold`, `SecondThreshold`, `LongPeriod`, `StandardPeriod`, `ShortPeriod`, `MaxRenewals`, `RenewalPeriod`), validando as restrições $0 < \text{FirstThreshold} < \text{SecondThreshold} < \text{MaxActiveLendings}$ e $\text{LongPeriod} > \text{StandardPeriod} > \text{ShortPeriod} > 0$.
  * Avaliar dinamicamente em *runtime* (Secção 4), em cada pedido de criação (`POST /api/lendings`) ou renovação (`POST /api/lendings/{year}/{seq}/renew`), o histórico do leitor nos últimos `HistoryPeriod` meses (`MinCompletedLendings`, `MinOnTimeRatio` e 0 empréstimos em atraso) para selecionar entre `Adaptive` e `Standard`.
* **FR04 — Catálogo e Serviços Base do LMS (1.1 Context):**
  * Preservar as funcionalidades existentes de gestão de Livros, Géneros, Autores, Leitores, Utilizadores e Empréstimos.

---

### 2.2. Cenários de Atributos de Qualidade (`ASR01`–`ASR07`)

| ID | Atributo de Qualidade | Fonte e Estímulo | Ambiente e Artefacto | Resposta Arquitetural | Medida Quantificável da Resposta |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **ASR01** | **Extensibilidade** *(Modifiability — 1.2.1)* | **Fonte:** Programador.<br/>**Estímulo:** Adicionar uma 3.ª fonte bibliográfica (`CrossRefAdapter`), um 4.º canal de notificação ou uma nova política de empréstimo. | **Ambiente:** Tempo de desenvolvimento.<br/>**Artefacto:** `bookmanagement`, `notificationmanagement`, `lendingmanagement`. | O novo componente é integrado implementando a porta abstrata (`BookInfoProvider`, `NotificationSender` ou `LendingPolicy`) sem alterar serviços ou entidades de domínio. | **0 classes de domínio ou serviço aplicacional modificadas** ($M_{\text{core}} = 0$); **1 nova classe adaptadora/estratégia** + registo declarativo na configuração; **100% dos testes prévios passam**. |
| **ASR02** | **Configurabilidade em Setup Time** *(1.2.2)* | **Fonte:** Administrador.<br/>**Estímulo:** Alterar fornecedores ativos (`bibliographic.providers`), canais (`notification.channels`) ou limiares de empréstimo (`lending.policy.*`). | **Ambiente:** Arranque da aplicação (*setup time*).<br/>**Artefacto:** `BibliographicConfig`, `NotificationConfig`, `LendingPolicyProperties`. | O contentor IoC instancia e ordena os adaptadores selecionados e valida os invariantes numéricos das políticas no arranque. | **0 linhas de código Java alteradas e 0 recompilações**; parâmetros inválidos lançam `IllegalArgumentException` (*fail-fast*). |
| **ASR03** | **Configurabilidade em Runtime** *(1.2.2 / Spec Secção 4)* | **Fonte:** Leitor / Bibliotecário.<br/>**Estímulo:** Pedido de empréstimo (`POST /api/lendings`) ou renovação (`POST /api/lendings/{y}/{s}/renew`). | **Ambiente:** Execução normal (*runtime*).<br/>**Artefacto:** `LendingServiceImpl`, `LendingPolicyResolver`. | O `LendingPolicyResolver` avalia o histórico recente do leitor (`HistoryPeriod`, `MinCompletedLendings`, `MinOnTimeRatio`, atrasos) e seleciona `Adaptive` ou `Standard`. | Resolução em memória em **$< 5\text{ ms}$** sem reiniciar o serviço; mudança imediata para `Standard` se o leitor tiver atrasos ativos. |
| **ASR04** | **Testabilidade** *(Testability — 1.2.3 / 1.3.3.4)* | **Fonte:** Pipeline CI/CD / Equipa.<br/>**Estímulo:** Execução de `mvn clean verify -Pmutation` em ambiente isolado sem internet. | **Ambiente:** Build / CI.<br/>**Artefacto:** Todos os módulos do LMS. | As dependências externas e repositórios são substituídos por *Test Doubles* (Mockito) nos testes unitários e H2 em memória nos testes de integração. | **100% de execução determinística offline** (**433 testes**: 386 Surefire + 47 Failsafe); cobertura core **75%–100%** e **Test Strength PITest global de 82%** (**88% em `AdaptiveLendingPolicy`**). |
| **ASR05** | **Interoperabilidade** *(Interoperability — 1.3.3.1)* | **Fonte:** APIs Externas (Google Books, Open Library, CrossRef).<br/>**Estímulo:** Retorno de payloads JSON com esquemas heterogéneos e campos parciais. | **Ambiente:** Execução normal.<br/>**Artefacto:** `GoogleBooksAdapter`, `OpenLibraryAdapter`, `CrossRefAdapter`, `CompositeBookInfoProvider`. | Cada adaptador traduz o JSON nativo para o modelo canónico `BookDetailsDTO`, e o `CompositeBookInfoProvider` funde campos em falta (*field-level merging*). | **0 dependências de esquemas JSON externos no domínio**; devolução de `BookDetailsDTO` enriquecido quando qualquer fonte ativa responde. |
| **ASR06** | **Resiliência e Tolerância a Falhas** *(Reliability)* | **Fonte:** Fornecedor HTTP ou gateway de notificação.<br/>**Estímulo:** Timeout, erro HTTP 5xx ou exceção num fornecedor/canal. | **Ambiente:** Operação com degradação externa.<br/>**Artefacto:** `CompositeBookInfoProvider`, `CompositeNotificationSender`, `LendingNotificationListener`. | Na pesquisa bibliográfica, avança para o próximo fornecedor na cadeia; nas notificações, isola a exceção por canal após o commit transacional (`AFTER_COMMIT`). | **0 transações de empréstimo abortadas** por falha de notificação; sucesso da pesquisa bibliográfica se pelo menos 1 fornecedor ativo responder. |
| **ASR07** | **Desempenho e Desacoplamento Transacional** *(Performance)* | **Fonte:** Fluxo de criação/devolução de empréstimo.<br/>**Estímulo:** Publicação de evento de domínio após persistência. | **Ambiente:** Execução normal.<br/>**Artefacto:** `LendingServiceImpl`, `LendingNotificationListener`. | A publicação de `LendingCreatedEvent` / `LendingReturnedEvent` é tratada por `@TransactionalEventListener(phase = AFTER_COMMIT)` fora da secção crítica do commit JPA. | Retenção de locks na base de dados H2 durante o envio de notificações igual a **0 ms**. |

---

## 3. Fatores Arquiteturais e Restrições (*Constraints*)

### 3.1. Restrições Técnicas (`TC01`–`TC05`)
* **TC01 — Plataforma Java 17 e Spring Boot 3.2.5:** Utilização de Spring Web, Spring Data JPA, Spring Security, Spring Events e `@ConfigurationProperties`.
* **TC02 — Monólito Modular (DDD):** Manutenção da organização modular por subdomínios (`bookmanagement`, `lendingmanagement`, `readermanagement`, `notificationmanagement`, etc.) num único processo JVM.
* **TC03 — Persistência Relacional JPA/H2:** Manutenção da integridade transacional ACID e compatibilidade com o esquema relacional base.
* **TC04 — Exposição RESTful / JSON:** Manutenção dos contratos HTTP REST existentes e adição do endpoint de renovação (`POST /api/lendings/{year}/{seq}/renew`) e pesquisa externa (`GET /api/books/external/{isbn}`).
* **TC05 — Execução Offline Hermética de Testes:** Proibição de chamadas de rede reais durante `mvn test` e `mvn verify`, recorrendo a *Mocks/Stubs* nas fronteiras externas.

### 3.2. Limitações Organizacionais (`OL01`–`OL04`)
* **OL01 — Divisão Modular de Trabalho:** Definição antecipada das interfaces `BookInfoProvider`, `NotificationSender` e `LendingPolicy` para desenvolvimento paralelo sem conflitos.
* **OL02 — Prazo de Entrega (2026-11-01):** Priorização das três áreas funcionais do enunciado em três iterações focadas do ADD.
* **OL03 — Rigor Metodológico:** Adoção do método **ADD 3.0** articulado com o modelo **4+1 Vistas de Kruchten** e **C4 Model**.
* **OL04 — Metas de Qualidade e Testes:** Cobertura funcional dos 5 níveis de teste exigidos em `1.3.3.4` e validação por testes de mutação (PITest).

---

## 4. Priorização dos Drivers Arquiteturais (ADD 3.0 — Passo 2)

Os drivers são priorizados segundo **(Importância para o Negócio, Impacto Arquitetural)** — Alta (`H`), Média (`M`), Baixa (`L`) — definindo a sequência das três iterações do ADD:

| Driver Arquitetural | Importância Negócio | Impacto Arquitetural | Prioridade | Alocação no Processo ADD |
| :--- | :---: | :---: | :---: | :--- |
| **FR01 + ASR01 + ASR05 + ASR06** (Fontes Bibliográficas Heterogéneas, Fusão e Fallback) | **H** | **H** | **(H, H) — P1** | **Iteração 1 do ADD** (`BookInfoProvider`, `GoogleBooksAdapter`, `OpenLibraryAdapter`, `CrossRefAdapter`, `CompositeBookInfoProvider`, `BibliographicConfig`) |
| **FR02 + ASR01 + ASR06 + ASR07** (Notificações Multicanal Desacopladas por Eventos) | **H** | **H** | **(H, H) — P1** | **Iteração 2 do ADD** (`LendingCreated/Returned/OverdueEvent`, `LendingNotificationListener`, `NotificationSender`, `CompositeNotificationSender`, `NotificationConfig`) |
| **FR03 + ASR02 + ASR03 + ASR01** (Políticas de Empréstimo Standard e Adaptive em Runtime) | **H** | **H** | **(H, H) — P1** | **Iteração 3 do ADD** (`LendingPolicy`, `StandardLendingPolicy`, `AdaptiveLendingPolicy`, `LendingPolicyResolver`, `LendingPolicyProperties`, `Lending.renew`) |
| **ASR04 + TC05** (Testabilidade Multi-Nível e Isolamento Offline) | **H** | **M** | **(H, M) — P2** | **Transversal (Iterações 1–3)** (Injeção por construtor, portas abstratas e separação Surefire/Failsafe) |
| **TC01–TC04 + FR04** (Spring Boot, Monólito Modular, JPA/H2, REST) | **H** | **H** | **Restrições Base** | Governa todas as decisões estruturais sem quebrar contratos legados |

---

## 5. Critério Operacional de «Impacto Limitado» e Compromissos (*Trade-offs*)

### 5.1. Critério Operacional de «Impacto Limitado»
Para tornar verificável a exigência do enunciado de suportar novas fontes bibliográficas, novos canais de notificação e novas políticas de empréstimo com *«impacto limitado na funcionalidade existente»*, adotam-se três métricas objetivas:
1. **Zero Modificações no Domínio e Serviços Aplicacionais ($M_{\text{core}} = 0$):** A adição de um novo fornecedor bibliográfico (comprovada pela inclusão real do [`CrossRefAdapter`](../../../src/main/java/pt/psoft/g1/psoftg1/bookmanagement/infrastructure/external/CrossRefAdapter.java)), canal de notificação ou política de empréstimo não altera nenhuma linha em `Book`, `Lending`, `ReaderDetails`, `BookServiceImpl` ou `LendingServiceImpl`.
2. **Extensão Confinada ($A_{\text{new}} = 1$):** Cada extensão resume-se à criação de **1 nova classe concreta** que implementa a interface respetiva (`BookInfoProvider`, `NotificationSender` ou `LendingPolicy`) e ao seu registo na classe de configuração (`BibliographicConfig`, `NotificationConfig` ou `LendingPolicyResolver`).
3. **Zero Regressões ($R = 0$):** 100% dos testes unitários e de integração existentes continuam a passar sem alterações.

### 5.2. Compromissos Arquiteturais (*Trade-offs*)
* **Extensibilidade (`ASR01`) vs. Indireção Estrutural:** A introdução de portas abstratas e compositores (`CompositeBookInfoProvider`, `CompositeNotificationSender`, `LendingPolicyResolver`) aumenta o número de classes face ao *system-as-is*, mas isola cada integração externa e reduz a complexidade ciclomática dos serviços de domínio.
* **Configurabilidade em *Setup Time* (`ASR02`) vs. Validação em Tempo de Compilação:** Externalizar limiares (`FirstThreshold`, `SecondThreshold`, `MinOnTimeRatio`) para `application.properties` transfere a deteção de erros de configuração para o arranque/execução, sendo mitigada pela validação explícita de invariantes nos construtores de `StandardLendingPolicy`, `AdaptiveLendingPolicy` e `LendingPolicyResolver`.
* **Reavaliação Dinâmica em *Runtime* (`ASR03`) vs. Consulta Adicional ao Histórico:** Reavaliar a elegibilidade para a `AdaptiveLendingPolicy` em cada criação e renovação de empréstimo exige consultar o histórico do leitor nos últimos `HistoryPeriod` meses (`listByReaderNumber`), trocando uma consulta adicional à base de dados por precisão imediata sempre que o perfil de cumprimento do leitor se altera.
