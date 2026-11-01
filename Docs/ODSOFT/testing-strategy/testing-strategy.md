# Estratégia de Testes Automatizados, Avaliação de Qualidade e Deteção de Defeitos

## 1. Limitações da Linha de Base (*As-Is*)
Conforme estabelecido em [`Docs/ODSOFT/system-as-is/system-as-is.md`](../system-as-is/system-as-is.md):
* **Mistura de Âmbitos:** Os testes executados durante `mvn test` misturavam testes unitários com testes de integração pesados (`@SpringBootTest`), exigindo o arranque completo do contexto e inicialização da base de dados.
* **Ausência de Fase de Testes de Integração:** O build Maven apenas configurava o `maven-surefire-plugin`. O `maven-failsafe-plugin` estava ausente, não existindo uma fase dedicada a testes de integração (`mvn verify`).
* **Testes de Segurança Desativados e Defeito Fatal Silencioso:** Os testes de segurança estavam comentados (`TestAuthApi.java`). Por conseguinte, um defeito fatal em `UserService.java` (duplo hashing BCrypt de palavras-passe) permaneceu oculto, inviabilizando todos os logins com HTTP 401.
* **Score Baixo de Mutação e Falsa Confiança:** O score de mutação inicial era de apenas **22%** (124/575 mutantes mortos), com 78% das falhas injetadas a sobreviver. Os testes limitavam-se a validar getters triviais sem verificar fronteiras comportamentais ou invariantes de negócio.

---

## 2. Arquitetura da Estratégia de Testes: Âmbitos, Níveis e Classificação

Para cumprir os requisitos de conceção de uma estratégia de testes automatizados com âmbitos e níveis bem definidos (Goal 1.3.4), a suite organiza-se de acordo com as fronteiras de abstração do Objeto em Teste (SUT), equilibrando **verificação** (avaliação de código e contratos face a especificações técnicas) e **validação** (avaliação do comportamento ponta a ponta face a fluxos de negócio).

### 2.1. Âmbitos do SUT e Níveis de Teste: Isolamento e Fronteiras de Integração

1. **Nível de Testes Unitários (Unidades Atómicas e Agregados — `maven-surefire-plugin`, fase `test`):**
   * **Granularidade 1 (Unidades Atómicas):** Value Objects de Domínio e utilitários isolados (ex.: `Isbn`, `Title`, `BirthDate`, `Password`, `LendingNumber`). Possuem zero dependências externas, executam em sub-milissegundos e garantem regras estritas de validação.
   * **Granularidade 2 (Agregados e Serviços Aplicacionais):** Agregados compósitos (`Lending`, `Book`, `ReaderDetails`, `User`), Políticas de Domínio (`StandardLendingPolicy`, `AdaptiveLendingPolicy`), Adaptadores Externos (`GoogleBooksAdapter`, `OpenLibraryAdapter`, `CrossRefAdapter`) e Serviços Aplicacionais (`LendingServiceImpl`, `UserService`, `BookServiceImpl`). Os colaboradores (repositórios, clientes HTTP, codificadores de segurança) são substituídos por **Test Doubles** para avaliar invariantes e ramos de negócio em memória sem sobrecarga de bases de dados ou frameworks.
2. **Nível de Testes de Integração (Componentes e Subsistemas — `maven-failsafe-plugin`, fase `verify`):**
   * Combina componentes eliminando duplos internos para verificar contratos através de fronteiras arquiteturais:
     * Controlador $\leftrightarrow$ Cadeia de Filtros do Spring Security (validação de controlo de acessos por perfil: `READER` vs. `LIBRARIAN`).
     * Serviço Aplicacional $\leftrightarrow$ Eventos de Domínio (`@TransactionalEventListener`) $\leftrightarrow$ Spring Data JPA $\leftrightarrow$ Base de Dados Relacional H2.
   * **Contenção de Recursos e Concorrência:** Valida o bloqueio otimista (`@Version`) e rollbacks transacionais sob acessos concorrentes (ex.: condições de corrida na devolução de livros), impossíveis de testar em testes unitários isolados.
3. **Nível de Testes de Sistema e Aceitação Operacional (Âmbito Ponta a Ponta):**
   * **Verificação de Sistema (fase `verify`):** Cenários REST HTTP completos (`BookExternalInfoIT`, `AuthApiIntegrationTest`, `LendingNotificationIntegrationIT`) exercitando a aplicação empacotada contra dados controlados em H2 e mocks nas fronteiras de rede.
   * **Aceitação Operacional e Prontidão (estágios de `Deploy DEV/UAT/PROD`):** Smoke tests contratuais automatizados ([`deploy/smoke-test.sh`](../../../deploy/smoke-test.sh) validando `/info` e `/actuator/health/readiness`) nos ambientes provisionados, articulados com o portão de aprovação humana em `lms-uat` antes da promoção a Produção.

### 2.2. Classificação Multidimensional da Suite de Testes

| Suite de Teste / Âmbito SUT | 1. Nível / Fase | 2. Técnica / Acessibilidade | 3. Atributo de Qualidade (ISO 25010) | 4. Nível de Automação | 5. Ambiente Alvo | 6. Tipo de Dados Utilizado |
|---|---|---|---|---|---|---|
| **Value Objects e Utilitários** (`IsbnTest`, `TitleTest`, `LendingNumberTest`, etc.) | Unitário (OUT Atómico) | **Caixa Opaca** (Partições de Equivalência / Análise de Valores Limite) + **Caixa Transparente** (Cobertura de Ramos) | Adequação Funcional (Integridade e validação de dados) | 100% Automatizado (Maven Surefire) | Dev Local / CI Runner | Valores limite e entradas inválidas sintéticas |
| **Raízes de Agregado e Eventos** (`BookTest`, `LendingTest`, `UserTest`, `LendingEventsTest`) | Unitário (OUT Granularidade 1) | **Caixa Transparente** (Estados internos, versionamento, aplicação de patches, eventos) | Adequação Funcional e Fiabilidade | 100% Automatizado (Maven Surefire) | Dev Local / CI Runner | Modelos de domínio sintéticos e datas limite |
| **Serviços Aplicacionais e Políticas** (`LendingServiceImplTest`, `BookServiceImplTest`, `*LendingPolicyTest`) | Unitário (OUT Granularidade 2) | **Caixa Transparente** (exercita ramos de decisão e exceções via Mocks/Stubs) | Adequação Funcional (Regras de negócio e invariantes) | 100% Automatizado (Maven Surefire) | Dev Local / CI Runner | Test doubles sintéticos (Stubs, Spies, Mocks) |
| **Gateways Bibliográficos e Notificações** (`OpenLibraryAdapterTest`, `GoogleBooksAdapterTest`, `CrossRefAdapterTest`, `CompositeBookInfoProviderTest`, `LendingNotificationListenerTest`, `*SenderTest`) | Unitário (OUT Granularidade 2) | **Caixa Transparente** (Cadeias de fallback, isolamento de falhas, parsing JSON, despacho de estratégias) | Modificabilidade, Configurabilidade e Tolerância a Falhas | 100% Automatizado (Maven Surefire) | Dev Local / CI Runner | Mocks de colaboradores HTTP/Mail/Webhook e DTOs sintéticos |
| **Controladores REST e Metadados** (`AuthorControllerTest`, `InfoControllerTest`, `LendingPolicyAdminControllerTest`) | Unitário (Slice Web) | **Semi-Transparente** (MockMvc isolado com double de serviço) | Adequação Funcional (Serialização HTTP e build info) | 100% Automatizado (Maven Surefire) | Dev Local / CI Runner | Payloads JSON sintéticos e respostas simuladas |
| **API de Segurança e RBAC** (`AuthApiIntegrationTest`, `AuthorControllerIntegrationTest`, etc.) | Integração (Componente de Subsistema) | **Caixa Opaca / Comportamental** (Endpoints HTTP sem doubles internos) | Segurança (Autenticação, perfis RBAC: `READER` vs `LIBRARIAN`) | 100% Automatizado (Maven Failsafe) | Dev Local / CI Runner (H2 Fake) | Credenciais sintéticas e tokens JWT assinados |
| **Repositórios de Persistência** (`LendingRepositoryIntegrationTest`, `AuthorRepositoryIntegrationTest`) | Integração (Camada de Dados) | **Caixa Transparente** (Execução direta de queries SQL / JPA sem doubles) | Fiabilidade e Adequação Funcional (Correção de consultas) | 100% Automatizado (Maven Failsafe) | Dev Local / CI Runner (H2 Fake) | Entidades relacionais de teste |
| **Integração de Serviços e Eventos** (`LendingServiceImplIT`, `BookExternalInfoIT`, `LendingNotificationIntegrationIT`) | Integração / Sistema (Interação entre Componentes) | **Caixa Opaca e Transparente** (Pilha completa: HTTP/Serviço $\leftrightarrow$ Eventos $\leftrightarrow$ JPA $\leftrightarrow$ BD) | Fiabilidade (Concorrência, Despacho Transacional de Eventos e Isolamento de Falhas) | 100% Automatizado (Maven Failsafe) | Dev Local / CI Runner (H2 Fake) | Fixtures relacionais e Contexto Spring |

### 2.3. Implementação de Test Doubles: Isolamento com Evidências de Código
Para garantir o isolamento unitário em componentes de Granularidade 2, a suite adota a **taxonomia de Test Doubles** (Gerard Meszaros / Martin Fowler) com exemplos concretos no código:

1. **Dummy (Preenchimento de Parâmetros):** Objetos passados unicamente para satisfazer assinaturas de métodos sem serem executados pelo cenário de teste.
   * *Exemplo de Código ([`BookTest.java`](../../../src/test/java/pt/psoft/g1/psoftg1/bookmanagement/model/BookTest.java)):*
     ```java
     // validGenre e authors são passados apenas para preencher a assinatura do construtor ao testar ISBN nulo
     assertThrows(IllegalArgumentException.class, () ->
         new Book(null, validTitle, null, validGenre, authors, null));
     ```
2. **Stub (Fornecedor de Entradas Indiretas):** Duplos pré-configurados que devolvem respostas pré-definidas para conduzir o SUT por ramos específicos de execução.
   * *Exemplo de Código ([`BookServiceImplTest.java`](../../../src/test/java/pt/psoft/g1/psoftg1/bookmanagement/services/BookServiceImplTest.java)):*
     ```java
     when(bookRepository.findByIsbn("9782826012092")).thenReturn(Optional.of(book));
     ```
3. **Fake (Implementação Rápida de Atalho):** Implementações funcionais com atalhos operacionais que as tornam inadequadas para produção.
   * *Exemplo de Código:* A **base de dados H2 em memória (`jdbc:h2:mem:testdb`)** utilizada nos testes Failsafe implementa semântica ANSI SQL relacional sem a sobrecarga de I/O em disco. *(Classes auxiliares como `UserTestDataFactory.java` aplicam o padrão **Test Data Builder / Object Mother** para geração de estado sintético).*
4. **Spy (Gravador de Invocações):** Duplos que registam argumentos e chamadas para inspeção posterior.
   * *Exemplo de Código ([`LendingServiceImplTest.java`](../../../src/test/java/pt/psoft/g1/psoftg1/lendingmanagement/services/LendingServiceImplTest.java)):*
     ```java
     ArgumentCaptor<Lending> lendingCaptor = ArgumentCaptor.forClass(Lending.class);
     verify(lendingRepository).save(lendingCaptor.capture());
     assertThat(lendingCaptor.getValue().getLendingNumber()).endsWith("/6");
     ```
5. **Mock (Verificação Comportamental):** Objetos pré-programados para validar interações face a uma especificação estrita de chamadas esperadas.
   * *Exemplo de Código ([`LendingServiceImplTest.java`](../../../src/test/java/pt/psoft/g1/psoftg1/lendingmanagement/services/LendingServiceImplTest.java)):*
     ```java
     verify(bookRepository, never()).findByIsbn(anyString());
     verify(lendingRepository, never()).save(any());
     ```

---

### 2.4. Especificação Funcional de Caixa Opaca: Partições de Equivalência e Análise de Valores Limite (C2.1)

Para satisfazer o subcritério **C2.1 (Testes Funcionais de Caixa Opaca — Classes)** sem depender da estrutura interna do código, os casos de teste foram derivados das especificações de domínio através de **Partições de Equivalência (EP)** e **Análise de Valores Limite (BVA)**:

| Classe / Componente (SUT) | Regra de Especificação / Contrato | Partição de Equivalência / Condição Limite | Tipo de Caso | Dados Concretos de Entrada | Resultado Esperado | Resultado Obtido (Método de Teste) |
| :--- | :--- | :--- | :---: | :--- | :--- | :--- |
| **`Isbn`** (`IsbnTest`) | ISBN-10 válido (checksum Mod 11) ou ISBN-13 (checksum EAN-13 Mod 10); não-nulo, não-vazio. | • Partição ISBN-13 válida<br/>• Partição ISBN-10 válida<br/>• Limite de comprimento inválido (9 ou 12 chars)<br/>• Dígito de controlo inválido (`9782826012099`)<br/>• Entrada nula ou vazia | Positivo<br/>Positivo<br/>Negativo<br/>Negativo<br/>Negativo | `"9782826012092"`<br/>`"282601209X"`<br/>`"123456789"`<br/>`"9782826012099"`<br/>`null` / `""` | Objeto construído<br/>Objeto construído<br/>`IllegalArgumentException`<br/>`IllegalArgumentException`<br/>`IllegalArgumentException` | **PASS** (`testValidIsbn13`, `testValidIsbn10`, `testInvalidIsbnChecksum`, `testNullOrEmptyIsbn`) |
| **`Title`** (`TitleTest`) | String não-vazia, comprimento $\in [1, 128]$ caracteres (`TITLE_MAX_LENGTH = 128`). | • Limite mínimo: 1 char<br/>• Partição nominal: 25 chars<br/>• Limite máximo válido: 128 chars<br/>• Acima do limite máximo: 129 chars<br/>• Nulo ou apenas espaços | Positivo<br/>Positivo<br/>Positivo<br/>Negativo<br/>Negativo | `"A"`<br/>`"Clean Architecture"`<br/>`"A".repeat(128)`<br/>`"A".repeat(129)`<br/>`"   "` / `null` | Aceite<br/>Aceite<br/>Aceite<br/>`IllegalArgumentException`<br/>`IllegalArgumentException` | **PASS** (`testBoundaryMaxLength128`, `testExceedsMaxLength129Throws`, `testBlankThrows`) |
| **`LendingServiceImpl`** (`LendingServiceImplTest`) | Leitor pode requisitar sse empréstimos ativos $< 3$ e empréstimos em atraso $== 0$. | • Empréstimos ativos = 0, 1, 2 (Válido)<br/>• Limite: Empréstimos ativos = 3 (Inválido)<br/>• Empréstimos em atraso $\ge 1$ (Inválido) | Positivo<br/>Negativo<br/>Negativo | `active = 2, overdue = 0`<br/>`active = 3, overdue = 0`<br/>`active = 1, overdue = 1` | `LendingView` gerado<br/>`LendingForbiddenException`<br/>`LendingForbiddenException` | **PASS** (`testCreate_Success`, `testCreate_ThrowsWhenReaderHasThreeOutstandingLendings`) |
| **`StandardLendingPolicy`** (`StandardLendingPolicyTest`) | Impõe limites máximos de empréstimos ativos, duração do empréstimo e multa por dia de atraso. | • Empréstimos ativos $= \text{max}-1$ (Limite válido)<br/>• Empréstimos ativos $= \text{max}$ (Limite inválido)<br/>• Dias de atraso $= 0$ vs $\ge 1$ | Positivo<br/>Negativo<br/>Positivo | `active = 2` (limite 3)<br/>`active = 3` (limite 3)<br/>`delay = 0` vs `delay = 5` | Validação bem-sucedida<br/>`LendingForbiddenException`<br/>`0 cêntimos` vs `5 * dailyFine` | **PASS** (`StandardLendingPolicyTest` — 6/6 testes) |
| **`GoogleBooksAdapter`, `OpenLibraryAdapter` e `CrossRefAdapter`** | Mapeia JSON externo por ISBN para `Optional<BookDetailsDTO>`; devolve `Optional.empty()` em 404/malformado/erro de rede. | • ISBN válido com JSON completo<br/>• ISBN válido com campos parciais<br/>• ISBN desconhecido (`totalItems: 0` / `{}`)<br/>• Erro HTTP 500 / Timeout | Positivo<br/>Positivo<br/>Negativo<br/>Negativo | ISBN `"9780132350884"`<br/>JSON sem editora<br/>ISBN desconhecido<br/>`RestClientException` | `Optional.of(dto)`<br/>`Optional.of(partialDto)`<br/>`Optional.empty()`<br/>`Optional.empty()` (Tratamento suave) | **PASS** (`GoogleBooksAdapterTest` 7/7, `OpenLibraryAdapterTest` 10/10, `CrossRefAdapterTest` 8/8) |
| **`CompositeNotificationSender`** | Despacha notificações em todos os canais configurados com isolamento de falhas. | • Todos os canais funcionam<br/>• 1 canal lança `RuntimeException`, restantes operacionais<br/>• Lista de canais vazia | Positivo<br/>Negativo / Falha<br/>Limite | `Email + SMS + Webhook`<br/>Webhook falha (`503`)<br/>`List.of()` | Os 3 canais invocados<br/>Email e SMS entregues com sucesso; exceção isolada<br/>Conclusão segura sem erros | **PASS** (`CompositeNotificationSenderTest` — 6/6 testes) |

* **Limitações dos Testes de Caixa Opaca ao Nível da Classe:** Embora as técnicas de EP e BVA garantam a validação de contratos de entrada/saída independentemente do código interno, não conseguem assegurar que ramos defensivos internos ou condicionais compostas são exercitados — justificando a sua complementaridade com **Testes de Caixa Transparente ao Domínio (C2.2)** e **Testes de Mutação PITest (C2.3)**.

---

### 2.5. Especificação Técnica de um Caso de Teste de Referência (`LendingServiceImplTest`)

Para ilustrar a classificação estrutural em todas as dimensões com base num método de teste concreto ([`LendingServiceImplTest.testCreate_ThrowsWhenReaderHasThreeOutstandingLendings`](../../../src/test/java/pt/psoft/g1/psoftg1/lendingmanagement/services/LendingServiceImplTest.java)):

| Dimensão | Caracterização Técnica |
| :--- | :--- |
| **Objeto em Teste (SUT)** | `LendingServiceImpl.create(CreateLendingRequest)` (Serviço Aplicacional de Granularidade 2). |
| **Colaboradores (DOCs)** | `LendingRepository`, `ReaderRepository`, `BookRepository`, `LendingViewMapper`, `ApplicationEventPublisher`. |
| **Estratégia de Isolamento** | Isolamento unitário via Mockito (`@ExtendWith(MockitoExtension.class)`): `LendingRepository` atua como **Stub** devolvendo 3 empréstimos ativos (`when(lendingRepository.listOutstandingByReaderNumber(...)).thenReturn(List.of(l1, l2, l3))`), enquanto `BookRepository` e `LendingRepository.save(...)` atuam como **Mocks** verificando a ausência de chamadas indevidas (`verify(..., never())`). |
| **Acessibilidade de Caixa** | Combina **Caixa Opaca BVA** (testando o limite superior `activeLendings == 3` do contrato) com **Verificação de Caixa Transparente e Mutação** (eliminando o mutante `ConditionalsBoundaryMutator` que altera `count >= 3` para `count > 3`). |
| **Natureza e Execução** | Teste funcional, determinístico e automatizado com tempo de execução inferior a `< 5 ms` na fase `test` do Maven. |

---

## 3. Arquitetura de Build e Ferramentas no Maven

O ciclo de vida de build no Maven ([`pom.xml`](../../../pom.xml)) assegura segregação estrita de fases, recolha separada de cobertura unitária e de integração, e análise de mutação de bytecode:

| Plugin | Versão | Objetivo / Fase | Responsabilidade e Configuração |
|---|---|---|---|
| **`maven-surefire-plugin`** | `3.1.2` | `test` | Executa testes unitários isolados (`**/*Test.java`). Exclui explicitamente testes de integração (`**/*IT.java`, `**/*IntegrationTest.java`). |
| **`jacoco-maven-plugin` (Unitário)** | `0.8.11` | `prepare-agent` (`initialize`)<br/>`report` (`test`) | Regista a cobertura unitária em `target/jacoco.exec` e gera relatórios XML/HTML em `target/site/jacoco/jacoco.xml`. |
| **`pitest-maven`** (`-Pmutation`) | `1.15.8` | `mutationCoverage` (`test`) | Executa testes de mutação imediatamente a seguir aos testes unitários na fase `test` (`*Test` contra classes de domínio/serviço, 4 threads, limiar $\ge 30\%$). |
| **`maven-failsafe-plugin`** | `3.1.2` | `integration-test`<br/>`verify` | Executa testes de integração e de sistema (`**/*IT.java`, `**/*IntegrationTest.java`) contra H2 após o `package`. |
| **`jacoco-maven-plugin` (Integração)** | `0.8.11` | `prepare-agent-integration`<br/>`report-integration` (`verify`) | Regista a cobertura de integração em `target/jacoco-it.exec` e gera relatórios XML/HTML em `target/site/jacoco-it/jacoco.xml`. |
| **`spotbugs-maven-plugin`** | `4.8.3.1` | `check` (`verify`) | Executa análise estática de bytecode (SAST) na fase `verify` com recurso a `spotbugs-exclude.xml`. |

### 3.1. Configurações de Portabilidade
* **JVM e MockMaker Subclass:** Fixou-se `mock-maker-subclass` em `src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker` para prevenir problemas de self-attachment do ByteBuddy em JDK 17/21 e executores containerizados.
* **Ficheiros Explícitos de Dados JaCoCo:** A passagem de `-Djacoco.dataFile=target/jacoco.exec -Djacoco-it.dataFile=target/jacoco-it.exec` no `.Jenkinsfile` assegura caminhos binários consistentes em agentes Windows e Linux.

---

## 4. Implementação da Suite de Testes (433 Testes Automatizados)

A suite de testes foi expandida de 102 testes misturados (linha de base) para **433 testes automatizados** segregados por ciclo de vida:

```
Total da Suite de Testes: 433 Testes Automatizados (100% Taxa de Aprovação — Build #38)
├── Testes Unitários Surefire (mvn test, 55 classes): 386 testes (~14s de execução)
└── Testes de Integração Failsafe (mvn verify, 12 classes): 47 testes (~45s de execução)
```

### 4.1. Suite de Testes Unitários (`src/test/java/.../*Test.java` — 386 Testes)
Executados pelo Maven Surefire (`mvn test`) em memória sem sobrecarga de contexto Spring:

1. **`LendingServiceImplTest.java` (20 testes):** Limite de 3 livros em simultâneo (`LendingForbiddenException`), bloqueio por livros em atraso, cálculo de multas, publicação de eventos de domínio (`LendingCreatedEvent`, `LendingReturnedEvent`, `LendingOverdueEvent`), entidades não encontradas (`NotFoundException`), validação de datas e inspeção de argumentos com `ArgumentCaptor<Lending>`.
2. **`BookServiceImplTest.java` (22 testes) e `BookDetailsDTOTest.java` (8 testes):** Deteção de conflitos de ISBN (`ConflictException`), tratamento de género inexistente, atualizações patch com bloqueio otimista, recomendações por género e enriquecimento bibliográfico externo via `BookInfoProvider`.
3. **`AuthorServiceImplTest.java` (17 testes):** CRUD completo, pesquisa por nome, paginação, consultas de coautores e limpeza de fotografias.
4. **`UserServiceTest.java` (19 testes):** Criação de `Reader` e `Librarian` sem duplo hashing BCrypt, deteção de utilizadores duplicados, validação de nomes proibidos, operações CRUD e extração de claims JWT.
5. **`ReaderServiceImplTest.java` (19 testes) e `NotificationRequestTest.java` (2 testes):** Registo de leitores, numeração sequencial, nomes proibidos, interesses em géneros, analítica de leitores de topo e atualizações de perfil.
6. **`GenreServiceImplTest.java` (12 testes):** Registo de géneros, ranking de géneros por volume de livros, médias mensais de empréstimo e validação de intervalos de datas.
7. **Gateways Bibliográficos e Adaptadores (`bookmanagement.infrastructure.gateways` — 31 testes):**
   * `CrossRefAdapterTest` (8 testes): Extração de JSON em `message.items[]`, parsing de autores, sanitização de tags JATS/XML e resiliência a falhas HTTP.
   * `OpenLibraryAdapterTest` (10 testes): Mapeamento de payload JSON, campos ausentes, resiliência a falhas de rede e respostas vazias.
   * `GoogleBooksAdapterTest` (7 testes): Extração de `items[].volumeInfo`, parsing de autores e tratamento de erros.
   * `CompositeBookInfoProviderTest` (6 testes): Fallback em cadeia de responsabilidade entre fornecedores ordenados, junção de dados parciais e isolamento de falhas.
8. **Notificações Multicanal Baseadas em Eventos (`readermanagement.infrastructure.notifications` e `lendingmanagement.events` — 43 testes):**
   * `LendingNotificationListenerTest` (9 testes): Tratamento de eventos `@TransactionalEventListener(phase = AFTER_COMMIT)` em criação, devolução e atraso de empréstimos.
   * `WebhookNotificationSenderTest` (7 testes), `SmsNotificationSenderTest` (8 testes), `EmailNotificationSenderTest` (7 testes): Despacho de estratégias por canal e tratamento de erros.
   * `CompositeNotificationSenderTest` (6 testes) e `LendingEventsTest` (6 testes): Difusão multicanal, isolamento de falhas caso um canal individual falhe e imutabilidade dos eventos.
9. **Controladores Web e Configuração (24 testes):** `AuthorControllerTest` (7), `NotificationConfigTest` (8), `BibliographicConfigTest` (7) e `InfoControllerTest` (2).
10. **Modelos de Domínio e Value Objects Partilhados (126 testes):**
    * **Value Objects e Utilitários (11 classes, 54 testes):** `IsbnTest` (7), `TitleTest` (7), `LendingNumberTest` (8), `NameTest` (6), `BioTest` (5), `StringUtilsCustomTest` (5), `BirthDateTest` (4), `PhoneNumberTest` (4), `DescriptionTest` (4), `PhotoTest` (2), `ForbiddenNameTest` (2).
    * **Entidades e Raízes de Agregado (7 classes, 72 testes):** `LendingTest` (21), `BookTest` (13), `UserTest` (9), `ReaderTest` (9), `AuthorTest` (9), `ReaderDetailsTest` (7), `GenreTest` (4).
11. **Políticas de Empréstimo e Padrão Strategy Dinâmico (`lendingmanagement.policies` — 62 testes em 7 suites):**
    * `AdaptiveLendingPolicyTest` (34 testes), `StandardLendingPolicyTest` (6 testes), `LendingPolicyResolverTest` (6 testes), `LendingPolicyAdminControllerTest` (5 testes), `ShortTermLendingPolicyTest` (4 testes), `PremiumReaderLendingPolicyTest` (4 testes), `LongTermLendingPolicyTest` (3 testes).

**Resultados Surefire (Build #38):** **386 testes executados, 0 falhas, 0 erros, 0 ignorados.**

### 4.2. Suite de Testes de Integração (`src/test/java/.../*IT.java`, `*IntegrationTest.java` — 47 Testes)
Executados pelo Maven Failsafe (`mvn verify`) contra Spring Security, Spring Data JPA, eventos transacionais de domínio e base de dados H2:

1. **`AuthApiIntegrationTest.java` (4 testes):** Fluxo de autenticação JWT (`POST /api/public/login`), autorregisto de leitor (`POST /api/public/register`) e rejeição de credenciais inválidas (`401`).
2. **`AuthorControllerIntegrationTest.java` (4 testes):** Verificação de RBAC (`LIBRARIAN` `201 Created` vs `READER` `403 Forbidden` vs anónimo `401 Unauthorized`).
3. **`LendingControllerIntegrationTest.java` (5 testes):** RBAC (`LIBRARIAN` cria vs `READER` devolve) e validação de versão para bloqueio otimista.
4. **`BookControllerIntegrationTest.java` (8 testes) e `BookExternalInfoIT.java` (5 testes):**
   * RBAC em criação/atualização de livros (`LIBRARIAN`) e recomendações (`READER`).
   * Consulta bibliográfica externa ponta a ponta (`GET /api/books/external/{isbn}`), enriquecimento automático na criação (`PUT /api/books/{isbn}`), enriquecimento via CrossRef e fallback entre fornecedores quando o principal falha.
5. **`GenreControllerIntegrationTest.java` (5 testes):** Endpoints analíticos e de RBAC (`top5`, `lendingsPerMonthLastTwelveMonths`).
6. **`LendingServiceImplIT.java` (4 testes):** Persistência multi-entidade e **contenção de concorrência com bloqueio otimista** (`StaleObjectStateException` quando duas transações tentam atualizar a mesma versão do empréstimo).
7. **`LendingNotificationIntegrationIT.java` (3 testes):** Publicação de eventos transacionais (`AFTER_COMMIT`) em criação e devolução, verificando despacho multicanal e isolamento de falhas quando um canal lança exceção.
8. **`LendingRepositoryIntegrationTest.java` (7 testes), `AuthorRepositoryIntegrationTest.java` (1 teste) e `AuthorServiceImplIntegrationTest.java` (1 teste):** Consultas JPA personalizadas, filtros de atraso e persistência transacional.

**Resultados Failsafe (Build #38):** **47 testes executados, 0 falhas, 0 erros, 0 ignorados.**

---

### 4.3. Matriz de Cenários de Sistema de Caixa Opaca Ponta a Ponta (C2.5)

Para satisfazer o subcritério **C2.5 (Testes Funcionais de Sistema — Caixa Opaca)**, o sistema é validado através das suas interfaces REST HTTP externas face aos requisitos funcionais e operacionais, combinando testes de sistema em Spring Boot (`@SpringBootTest` + MockMvc) no Maven `verify` com smoke tests contratuais pós-deployment ([`deploy/smoke-test.sh`](../../../deploy/smoke-test.sh)) nas instâncias ativas de `DEV`, `UAT` e `PROD`:

| ID do Cenário e Requisito | Artefacto de Teste / Script | Pré-Condições | Dados Controlados de Entrada | Saída Externa Esperada | Resultado Obtido |
| :--- | :--- | :--- | :--- | :--- | :---: |
| **SYS-01: Pesquisa Bibliográfica Externa, Fallback e Criação Auto-Enriquecida** (FR01, FR05, ASR01) | [`BookExternalInfoIT.java`](../../../src/test/java/pt/psoft/g1/psoftg1/bookmanagement/BookExternalInfoIT.java) | Contexto Spring ativo, esquema H2 inicializado, `Author` e `Genre ("Software")` semeados, `LIBRARIAN` autenticado. | 1. `GET /api/books/external/9780201633610`<br/>2. `PUT /api/books/9780201633610?authors=<id>` (título e género omitidos)<br/>3. Consulta CrossRef e fallback por falha do fornecedor primário | 1. `200 OK` com metadados externos (`"Design Patterns E2E"`).<br/>2. `201 Created` com título, descrição e género enriquecidos automaticamente.<br/>3. Fornecedor secundário enriquece o livro quando o primeiro falha. | **PASS (5/5)** |
| **SYS-02: Registo de Utilizador, Autenticação JWT e Aplicação de RBAC** (FR03, Segurança) | [`AuthApiIntegrationTest.java`](../../../src/test/java/pt/psoft/g1/psoftg1/auth/api/AuthApiIntegrationTest.java) e [`AuthorControllerIntegrationTest.java`](../../../src/test/java/pt/psoft/g1/psoftg1/authormanagement/api/AuthorControllerIntegrationTest.java) | Cadeia de filtros Spring Security e codificador/descodificador JWT ativos; base H2 limpa. | 1. `POST /api/public/register` (dados de Reader)<br/>2. `POST /api/public/login` (palavra-passe correta vs incorreta)<br/>3. `POST /api/authors` como `READER` vs `LIBRARIAN` | 1. `201 Created`<br/>2. `200 OK` + cabeçalho `Authorization` JWT (ou `401 Unauthorized` com palavra-passe errada)<br/>3. `403 Forbidden` (`READER`) vs `201 Created` (`LIBRARIAN`). | **PASS (8/8)** |
| **SYS-03: Criação de Empréstimo, Devolução e Isolamento de Falhas em Notificações** (FR04, FR06, ASR06) | [`LendingNotificationIntegrationIT.java`](../../../src/test/java/pt/psoft/g1/psoftg1/readermanagement/infrastructure/notifications/LendingNotificationIntegrationIT.java) | Contexto transacional ativo (`@TransactionalEventListener(phase = AFTER_COMMIT)`), Livro e Leitor semeados, canais configurados. | Criar empréstimo e devolver empréstimo; simular falha (`RuntimeException`) num canal de notificação. | Transação do empréstimo comita (`201`/`200`) e persiste em BD; canais funcionais recebem notificação; canal em falha é isolado sem rollback da BD. | **PASS (3/3)** |
| **SYS-04: Bloqueio Otimista e Contenção Concorrente na Devolução de Empréstimo** (FR04, Fiabilidade) | [`LendingControllerIntegrationTest.java`](../../../src/test/java/pt/psoft/g1/psoftg1/lendingmanagement/api/LendingControllerIntegrationTest.java) e [`LendingServiceImplIT.java`](../../../src/test/java/pt/psoft/g1/psoftg1/lendingmanagement/services/LendingServiceImplIT.java) | Empréstimo ativo em BD com `version = 0`. Dois pedidos concorrentes de devolução submetidos com versão inicial `0`. | Pedido A: `PATCH /api/lendings/{y}/{seq}` (`If-Match: 0`)<br/>Pedido B: Atualização concorrente com versão obsoleta `0` | Pedido A comita (`version -> 1`); Pedido B é rejeitado (`StaleObjectStateException`). | **PASS (9/9)** |
| **SYS-05: Verificação Contratual e Prontidão Operacional Pós-Deployment** (Goal 1.3.5) | [`deploy/smoke-test.sh`](../../../deploy/smoke-test.sh) | `app.jar` instalado e iniciado via `psoft-g1@<env>.service` em `vs806` (`8081`/`8082`) ou `vs336` (`8080`). | HTTP `GET /info` e `GET /actuator/health/readiness` (até 30 tentativas $\times$ 2s). | HTTP `200 OK`; JSON `application == "psoft-g1"`, `environment == "<dev\|uat\|prod>"`, `commit == "<GIT_SHA>"` e `status == "UP"`. | **PASS** em `DEV`, `UAT` e `PROD` |
| **SYS-06: Coleções de Testes de Sistema no Postman** | [`Docs/ARQSOFT/Postman/new/Psoft-G1.postman_collection.json`](../../ARQSOFT/Postman/new/Psoft-G1.postman_collection.json) e [`Docs/ARQSOFT/Postman/Psoft-G1.postman_collection.json`](../../ARQSOFT/Postman/Psoft-G1.postman_collection.json) | Aplicação em execução com dados bootstrap e variáveis de ambiente (`Psoft-G1.postman_environment.json`). | • **Nova coleção modular (`new`):** **47 pedidos** organizados por domínio (`Auth`, `Author`, `Book/External`, `Reader`, `Lendings/Renew`).<br/>• **Coleção original:** **183 pedidos compostos** em fluxos `UserStories`. | Validação contratual HTTP/JSON de todos os endpoints REST legados e novos. | **PASS (47/47 na coleção nova)** |

* **Análise de Reprodutibilidade e Limitações (C2.5):**
  1. **Reprodutibilidade Offline:** Fornecedores HTTP externos terceiros (Google Books, Open Library, CrossRef, gateways SMTP/SMS) são simulados nas fronteiras durante o Maven `verify` para prevenir falhas por indisponibilidade de rede ou quotas externas.
  2. **Compromisso de Fidelidade da Base de Dados:** Os testes Failsafe correm contra o fake em memória H2 (`jdbc:h2:mem:testdb`) para obter execuções rápidas; discrepâncias dialetais face a motores de produção são mitigadas pela utilização de consultas padrão JPA e testes de fumo live nos servidores Linux de destino.

---

## 5. Avaliação Empírica: Deteção de Defeitos e Prova de Regressão

### 5.1. Deteção de Defeito Real: Bug de Duplo Hashing BCrypt em `UserService`
* **Localização do Defeito:** `src/main/java/pt/psoft/g1/psoftg1/usermanagement/services/UserService.java` (Linha 94)
* **Mecanismo:** Ao criar utilizadores, os métodos `Reader.newReader(...)` e `Librarian.newLibrarian(...)` invocavam `User.setPassword(...)`, que já codificava a palavra-passe com o `PasswordEncoder`. De seguida, o `UserService.java` executava `user.setPassword(passwordEncoder.encode(request.getPassword()))`, cifrando pela segunda vez uma string já codificada e gerando sempre `401 Unauthorized` nos pedidos de login.
* **Deteção e Correção:** A automatização de `AuthApiIntegrationTest.java` revelou imediatamente esta falha. A remoção da linha redundante de codificação restabeleceu o funcionamento em todos os testes unitários e de integração (`UserServiceTest`, `UserTest`).

### 5.2. Deteção de Regressões: Experiência de Injeção de Falhas em Regras de Negócio
1. **Falha Injetada:** Em `LendingServiceImpl.java`, alterou-se a verificação de limite de empréstimos ativos de `if (count >= 3)` para `if (count > 3)`.
2. **Observação:** A execução de `mvn test` capturou imediatamente a regressão em `LendingServiceImplTest.testCreate_ThrowsWhenReaderHasThreeOutstandingLendings`:
   ```
   [ERROR] LendingServiceImplTest.testCreate_ThrowsWhenReaderHasThreeOutstandingLendings:92
           Expected pt.psoft.g1.psoftg1.exceptions.LendingForbiddenException to be thrown, but nothing was thrown.
   ```

### 5.3. Deteção de Regressões de Concorrência e Contenção de Recursos
No teste `LendingServiceImplIT.testConcurrentReturn_ResourceContentionOptimisticLocking`, duas transações concorrentes tentam devolver o mesmo registo com `initialVersion = 0`. A transação 1 comita e incrementa o `@Version`; a transação 2 é rejeitada com `StaleObjectStateException`, provando que regressões de controlo de concorrência são sistematicamente intercetadas durante `mvn verify`.

---

## 6. Análise de Cobertura de Código — Verificação de Caixa Transparente ao Domínio (C2.2)

A cobertura JaCoCo é registada separadamente para testes unitários (`target/site/jacoco/jacoco.xml`) e de integração (`target/site/jacoco-it/jacoco.xml`), sendo apresentada no Jenkins via **Coverage Plugin (`recordCoverage`)**:

### 6.1. Cobertura Global do Repositório vs. Módulos Core de Domínio/Serviço (Build #38)
* **Cobertura Unitária Global (`Cobertura: testes unitários`):** **49,05% Cobertura de Linhas** (`2.295 / 4.679` linhas), **24,64% Cobertura de Ramos** (`965 / 3.917` ramos), **31,92% Cobertura de Instruções** (`9.888 / 30.978` instruções).
* **Cobertura de Integração Global (`Cobertura: testes de integração`):** **33,15% Cobertura de Linhas** (`1.551 / 4.679` linhas), **9,62% Cobertura de Ramos** (`377 / 3.917` ramos), **24,32% Cobertura de Instruções** (`7.534 / 30.978` instruções).
* **Por que razão a Cobertura Global difere dos Módulos Core:** O denominador global (`4.679` linhas) inclui controladores REST legados, mappers DTO antigos e classes de `Bootstrapper` não modificadas. Em contrapartida, em todos os **modelos de domínio core, serviços aplicacionais, políticas de empréstimo, gateways bibliográficos e componentes de notificação**, a cobertura unitária de linhas situa-se entre **75% e 100%**:

| Componente Core / Camada sob Teste | Cobertura de Instruções | Cobertura de Linhas | Cobertura de Ramos | Avaliação de Caixa Transparente |
|---|:---:|:---:|:---:|:---|
| **`GenreServiceImpl`** | **100%** (93/93) | **100%** (22/22) | **100%** (6/6) | Cobertura total de caminhos de analítica e datas |
| **`LendingServiceImpl`** | **98%** | **100%** (58/58) | **85%** | Regras completas de empréstimo, políticas e multas |
| **`AuthorServiceImpl`** | **68%** | **100%** (31/31) | **60%** | CRUD completo de autor e consultas |
| **`BookServiceImpl`** | **91%** | **96%** (80/83) | **70%** | Recomendações, atualizações e enriquecimento externo |
| **`UserService`** | **67%** | **93%** (40/43) | **43%** | Autenticação, CRUD de utilizadores e extração de claims |
| **`ReaderServiceImpl`** | **72%** | **90%** (86/96) | **53%** | Registo, sequenciação e atualizações de perfil |
| **`lendingmanagement.policies`** | **92%** | **95%** | **86%** | `Standard`, `Adaptive`, `ShortTerm`, `LongTerm`, `Premium` |
| **`bookmanagement.infrastructure.gateways`** | **94%** | **96%** | **88%** | `GoogleBooks`, `OpenLibrary`, `CrossRef`, `Composite` |
| **`readermanagement.infrastructure.notifications`** | **95%** | **97%** | **90%** | `Email`, `Sms`, `Webhook`, `Composite`, `Listener` |
| **`bookmanagement.model`** | **89%** | **94%** (101/108) | **82%** | Formatos ISBN, títulos, patches e fotografias |
| **`genremanagement.model`** | **81%** | **93%** (14/15) | **100%** | Cobertura total de invariantes e limites |
| **`readermanagement.model`** | **88%** | **92%** (103/112) | **70%** | RGPD, versionamento, telefone e data de nascimento |
| **`authormanagement.model`** | **88%** | **91%** (40/44) | **81%** | Invariantes de autor e biografia |
| **`usermanagement.model`** | **84%** | **87%** (55/63) | **66%** | Complexidade de palavra-passe, perfis e autenticação |
| **`lendingmanagement.model`** | **84%** | **86%** (91/106) | **80%** | Máquina de estados de empréstimo, atrasos e multas |
| **`shared.model`** | **68%** | **75%** (39/52) | **66%** | Utilitários de string, sanitização XSS e nomes proibidos |

---

## 7. Análise de Testes de Mutação (PIT) e Conformidade com Quality Gates (C2.3)

O PITest corre na fase `test` do Maven (`-Pmutation`) imediatamente após os testes unitários do Surefire, injetando mutações de bytecode em classes de domínio e serviço:

### 7.1. Métricas Globais e de Componentes Core (Build #38)
* **Cobertura de Linhas das Classes Mutadas:** **2.168 / 3.552 (61%)** (subida face aos 56% na Build #17 e 33% na linha de base).
* **Mutações Geradas no Total:** **3.798** (face a 575 na linha de base).
* **Mutações Mortas:** **1.078 (28%)** (face a 124 na linha de base — **+769,4% mutantes eliminados**).
* **Test Strength Global:** **82%** (percentagem de mutantes cobertos mortos ao longo de 2.944 execuções de testes).

| Pacote Core / Classe sob Teste | Cobertura de Linhas | Cobertura de Mutação | Test Strength | Estado do Quality Gate ($\ge 50\%$) |
|---|:---:|:---:|:---:|:---:|
| **`GenreServiceImpl`** | **100%** (22/22) | **100%** (10/10 mortos) | **100%** (10/10) | **APROVADO** |
| **`LendingServiceImpl`** | **100%** (58/58) | **100%** (32/32 mortos) | **100%** (32/32) | **APROVADO** |
| **`usermanagement.model`** | **87%** (55/63) | **100%** (22/22 mortos) | **100%** (22/22) | **APROVADO** |
| **`UserService`** | **93%** (40/43) | **89%** (24/27 mortos) | **100%** (24/24) | **APROVADO** |
| **`readermanagement.model`** | **92%** (103/112) | **85%** (50/59 mortos) | **88%** (50/57) | **APROVADO** |
| **`shared.model`** | **75%** (39/52) | **82%** (18/22 mortos) | **100%** (18/18) | **APROVADO** |
| **`AuthorServiceImpl`** | **100%** (31/31) | **77%** (20/26 mortos) | **80%** (20/25) | **APROVADO** |
| **`BookServiceImpl`** | **96%** (80/83) | **75%** (33/44 mortos) | **80%** (33/41) | **APROVADO** |
| **`authormanagement.model`** | **91%** (40/44) | **74%** (17/23 mortos) | **81%** (17/21) | **APROVADO** |
| **`genremanagement.model`** | **93%** (14/15) | **71%** (5/7 mortos) | **83%** (5/6) | **APROVADO** |
| **`bookmanagement.model`** | **94%** (101/108) | **70%** (66/94 mortos) | **88%** (66/75) | **APROVADO** |
| **`lendingmanagement.model`** | **86%** (91/106) | **69%** (34/49 mortos) | **83%** (34/41) | **APROVADO** |
| **`ReaderServiceImpl`** | **90%** (86/96) | **64%** (32/50 mortos) | **78%** (32/41) | **APROVADO** |

### 7.2. Análise Qualitativa de Mutantes Sobreviventes e Equivalentes
Nem todos os mutantes sobreviventes representam insuficiência nos testes. Uma auditoria aos 235 mutantes cobertos que sobreviveram identificou três categorias:
1. **Mutantes Equivalentes em Bytecode Gerado pelo Lombok:** Em DTOs e classes de valores com `@Data` ou `@Value` (ex.: `Role`, `SearchUsersQuery`, `EditUserRequest`), o Lombok sintetiza `hashCode()`, `equals()` e `toString()`. O PITest altera os multiplicadores de números primos no `hashCode()`, o que não modifica o comportamento observável do domínio.
2. **Guardas Defensivas Redundantes:** Em classes como `Book.java`, verificações defensivas de nulos em campos já garantidos pelo construtor (`if (description != null)`) não podem ser violadas por instâncias válidas de domínio, comportando-se como ramos defensivos equivalentes.
3. **Construtores e Acessores de Proxies JPA:** Construtores sem argumentos de visibilidade protegida e getters de versão exigidos para bytecode enhancement do Hibernate não são chamados diretamente na lógica isolada de domínio.

---

## 8. Métricas Comparativas: *As-Is* vs. *To-Be* (Build #38)

| Métrica | Linha de Base (*As-Is*) | Estado Alvo (*To-Be* — Build #38) | Melhoria Líquida | Impacto |
|:---|:---:|:---:|:---:|:---|
| **Total de Testes Automatizados** | 102 testes | **433 testes** | **+324,5%** | Proteção abrangente contra regressões no domínio e adaptadores |
| **Testes Unitários (`mvn test`)** | 102 (âmbitos misturados) | **386 (SUT isolado em memória)** | **+278,4%** | Feedback rápido (~14s) na fase `test` |
| **Testes de Integração (`mvn verify`)** | 0 configurados | **47 (Failsafe automatizado)** | **Novo Nível** | Validação de segurança, BD, concorrência, CrossRef e eventos |
| **Cobertura de Linhas JaCoCo (Unit / IT)** | Não configurada (~33% est. PIT) | **49,05% Unit / 33,15% IT global**<br/>**(75%–100% nos módulos core)** | **Evolução separada de Unit e IT** | Registada no Jenkins via treemaps do `recordCoverage` |
| **Serviços e Modelos Core $\ge 60\%$ Cobertura** | 5 em 13 (38%) | **13 em 13 (100%)** | **100% Conforme** | Cumpre o Portão de Cobertura de CI |
| **Serviços e Modelos Core $\ge 50\%$ Mutação** | 3 em 13 (23%) | **13 em 13 (100%)** | **100% Conforme** | Cumpre o Portão de Mutação de CI |
| **Mutações Mortas (PITest na fase `test`)** | 124 / 575 (22%) | **1.078 / 3.798 (28% global; 61% cob. linha)** | **+769,4% mutantes eliminados** | Injeção de falhas automática logo após testes unitários |
| **Test Strength Global (PITest)** | 70% | **82% (78%–100% no core)** | **+12,0 pts** | Elevada precisão de asserções no código coberto |
| **Defeitos Críticos Resolvidos** | Falhas ocultas de auth e SAST | **1 bug fatal de BCrypt + 3 bugs P1 SpotBugs corrigidos** | **0 Bugs P1** | Estabilidade de autenticação e entre SOs restabelecida |
