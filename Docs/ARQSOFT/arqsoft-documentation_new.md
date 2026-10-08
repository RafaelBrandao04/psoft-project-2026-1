# ARQSOFT — Documentação de Arquitetura (P1)

> Manutenção/Correção de um sistema — LMS (Library Management System), base de código `psoft-g1`.

Este documento segue o mesmo formato e notação (diagramas de componentes/pacotes UML e diagramas de sequência, à semelhança do trabalho de referência de um colega de um ano anterior) para descrever o *system-as-is* do LMS: vistas de Implementação, Lógica, Física e de Processos, cada uma decomposta em níveis de granularidade crescente (N1 → N4, quando aplicável).

Todas as vistas abaixo foram construídas por engenharia reversa do código-fonte real (`psoft-project-2024-g1`), não são especulativas.

---

## System-as-is

### Vista de Implementação

A vista de implementação de nível 1 é semelhante à vista lógica correspondente ao mesmo nível (ver secção seguinte) — o sistema é, hoje, uma única unidade implantável, pelo que não se repete o diagrama.

#### Nível 2

![VI_N2.jpg](System-as-is\VI-N2.png)

*VI N2 — LibraryManagementSystem decomposto em Backend e BD (pacotes).*

#### Nível 3

![VI_N3.jpg](System-as-is\VI-N3.png)

*VI N3 — pacotes Java reais dentro de Backend. As setas tracejadas mostram as dependências confirmadas nos imports: `bootstrapping` depende de todos os módulos de domínio; `auth`, `exceptions`, `configuration`, `external.service` e `shared` não são alvo de bootstrap.*

#### Nível 4

![VI_N4.jpg](System-as-is\VI-N4.png)

*VI N4 — decomposição do pacote `bookmanagement`: `API → Services → Repositories → Model`, com `Infrastructure.Repositories.Impl` a realizar `Repositories` e a depender de `Model` (confirmado pela árvore de pastas real do módulo).*

#### Mapeamento VI ↔ VL

![VI_to_VL.jpg](System-as-is\VI_to_VL.png)

*Manifest entre a Vista Lógica (camadas Onion/Clean, instanciadas para o módulo `BookManagement`) e a Vista de Implementação N4 (`bookmanagement`). `Routing`/`Drivers` (VL N3) não têm pacote próprio por módulo — manifestam-se antes na configuração global (`configuration`, driver JDBC), por isso não entram neste mapeamento.*

---

### Vista Lógica

#### Nível 1

![VL_N1.jpg](System-as-is\VL-N1.png)

*VL N1 — o LMS como um único componente lógico, expondo REST API e consumindo API Ninjas.*

#### Nível 2

![VL_N2.jpg](System-as-is\VL-N2.png)

*VL N2 — decomposição em Backend e BD, ligados pela interface BD API.*

#### Nível 3

![VL_N3.jpg](System-as-is\VL-N3.png)

*VL N3 — Backend decomposto nas camadas de arquitetura em uso no código (Frameworks & Drivers, Interface Adapters, Application Services, Enterprise Business Rules — um padrão Onion/Clean Architecture, confirmado pelos pacotes `api`, `services`, `model`, `repositories`, `infrastructure.repositories.impl` de cada módulo).*

---

### Vista Física

#### Nível 1

![VF_N1.jpg](System-as-is\VF-N1.png)

*VF N1 — um único nó físico (posto local) a correr o LMS.*

#### Nível 2

![VF_N2.jpg](System-as-is\VF-N2.png)

*VF N2 — o mesmo nó, agora com Backend e BD como componentes distintos (BD corre como servidor H2 TCP separado, não embutido).*

Não há ainda `Dockerfile`, `docker-compose` nem múltiplos ambientes — pelo que aprofundar esta vista além de N2 não acrescenta informação nova neste momento.

---

### Vista de Processos

Organizada por cenário (caso de uso), à semelhança do documento de referência.

#### Criar Empréstimo — Nível 1

![VP_N1.jpg](System-as-is\VP-N1.png)

*VP N1 — Bibliotecário e o LMS como um todo.*

#### Criar Empréstimo — Nível 2

![VP_N2.jpg](System-as-is\VP-N2.png)

*VP N2 — Backend e BD já distintos.*

#### Criar Empréstimo — Nível 3

![VP_N3.jpg](System-as-is\VP-N3.png)

*VP N3 — sequência real, reconstruída a partir de `LendingController`/`LendingServiceImpl`: verificação de empréstimos em atraso e do limite de 3 empréstimos ativos (`LendingForbiddenException` → 403), procura de livro/leitor (`NotFoundException` → 404 se ausentes), criação e persistência do `Lending`.*

#### Registar Livro — Nível 1

![VP_2_N1.jpg](System-as-is\VP-N1.png)

*VP N1 — Bibliotecário e o LMS como um todo.*

#### Registar Livro — Nível 2

![VP_2_N2.jpg](System-as-is\VP-N2.png)

*VP N2 — Backend e BD já distintos.*

#### Registar Livro — Nível 3

![VP_2_N3.jpg](System-as-is\VP-N3.png)

*VP N3 — sequência real, reconstruída a partir de `BookController`/`BookServiceImpl`: deteção de ISBN duplicado (`ConflictException` → 409), procura de autor e de género (`NotFoundException` → 404 se o género não existir), criação e persistência do `Book`. Simplificado para um único autor — o código real itera a mesma chamada `findByAuthorNumber` por cada autor pedido, ignorando os que não existem.*

#### Top 5 Géneros — Nível 1

![VP_3_N1.jpg](System-as-is\VP-N1.png)

*VP N1 — Bibliotecário e o LMS como um todo (endpoint só de leitura).*

#### Top 5 Géneros — Nível 2

![VP_3_N2.jpg](System-as-is\VP-N2.png)

*VP N2 — Backend e BD já distintos.*

#### Top 5 Géneros — Nível 3

![VP_3_N3.jpg](System-as-is\VP-N3.png)

*VP N3 — sequência real, reconstruída a partir de `GenreController`/`GenreServiceImpl`: consulta paginada (top 5 por nº de livros) e o caso de lista vazia (`NotFoundException` → 404), contrastando com os dois cenários anteriores por ser uma operação puramente de leitura, sem alterações de estado nem criação de objetos de domínio.*

---

## ASR (Architecturally Significant Requirements)

### 1. Requisitos Globais do Sistema

#### 1.1 Requisitos Funcionais (FR)

* **FR01 – Gestão de Livros (*Books*):** Registar, editar, consultar e listar livros do catálogo (ISBN, título, autores, descrição, géneros).
* **FR02 – Gestão de Autores e Géneros (*Authors & Genres*):** Manter o registo de autores e a taxonomia de géneros literários associados aos livros.
* **FR03 – Gestão de Leitores (*Readers*):** Gerir o ciclo de vida dos utilizadores/leitores (dados cadastrais, preferências de notificação).
* **FR04 – Gestão de Empréstimos (*Lendings*):** Criar e devolver empréstimos de livros a leitores, validando disponibilidade e regras de limite.
* **FR05 – Obtenção de Dados Bibliográficos Externos:** Obter e integrar metadados de livros a partir de APIs externas (ex.: Google Books, Open Library), harmonizando modelos de dados heterogéneos.
* **FR06 – Notificação a Leitores:** Notificar leitores sobre eventos relevantes de empréstimo (criação de empréstimo, aviso de devolução, atraso) via SMS, Email e/ou Webhook.
* **FR07 – Aplicação de Políticas de Empréstimo Dinâmicas:** Avaliar e aplicar regras e limites de empréstimo (duração, máximo de livros simultâneos, multas) com base no perfil do leitor ou tipo de obra.

---

### 2. Identificação e Classificação dos ASRs (*Architecturally Significant Requirements*)

#### ASR01: Extensibilidade de Fontes Bibliográficas
* **Atributo de Qualidade:** Modificabilidade (*Modifiability / Extensibility*)
* **Estímulo:** Necessidade de suportar um novo fornecedor de metadados bibliográficos (ex.: Worldcat, Biblioteca Nacional).
* **Fonte do Estímulo:** Equipa de desenvolvimento / Operações.
* **Ambiente:** Tempo de desenho/desenvolvimento (*Design time*).
* **Artefacto:** Módulo de integração bibliográfica / Serviços externos.
* **Resposta:** O novo fornecedor é adicionado através de uma nova implementação de interface (*Adapter/Gateway*), sem alterar a lógica de negócio principal nem as fontes existentes.
* **Medida de Resposta:**
  * Custo/esforço de implementação reduzido (impacto isolado numa única classe/pacote);
  * Zero alterações ao código do domínio ou de apresentação (princípio Aberto/Fechado - OCP).

---

#### ASR02: Configurabilidade de Provedores em *Setup Time*
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
  * Canais podem ser ativados individualmente ou em conjunto via configuração.

---

#### ASR04: Seleção Dinâmica de Políticas de Empréstimo em *Runtime*
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
* **Ambiente:** Tempo de build / Teste automatizado.
* **Artefacto:** Conjunto de testes automatizados e arquitetura de classes.
* **Resposta:** A arquitetura desacoplada permite testar as regras de domínio e as políticas isoladamente, simular/mockar os serviços externos (Google Books, gateways de email/SMS) e validar caixas opacas e transparentes com testes de mutação representativos.
* **Medida de Resposta:**
  * As classes de domínio são 100% testáveis sem necessidade de arrancar contexto Spring ou base de dados;
  * Alta taxa de mutantes mortos (*mutation test score*) nas classes críticas de negócio;
  * Testes de integração ($SUT = \text{controller} + \text{service} + \text{gateways}$) executam de forma determinística utilizando mocks/stubs para as APIs externas.

---

### 3. Mapeamento para Táticas Arquiteturais (ADD)

| ASR | Atributo de Qualidade | Tática Arquitetural (SEI / ADD) | Padrão / Solução Concreta |
| :--- | :--- | :--- | :--- |
| **ASR01** | Modificabilidade | *Abstract Common Services* / *Maintain Abstract Interfaces* | Padrão **Adapter** e **Gateway/Repository** para as APIs externas (Google Books, Open Library). |
| **ASR02** | Configurabilidade | *Defer Binding Time* / *Configuration Files* | Injeção condicional no Spring (`@ConditionalOnProperty`, `@ConfigurationProperties`). |
| **ASR03** | Modificabilidade | *Publish-Subscribe* / *Separate Concerns* | Padrão **Observer / Domain Events** combinado com o padrão **Composite** para múltiplos canais. |
| **ASR04** | Configurabilidade / Modificabilidade | *Defer Binding Time* / *Use an Intermediary* | Padrão **Strategy** em conjunto com **Factory / Specification** para resolução das políticas em runtime. |
| **ASR05** | Testabilidade | *Specialized Interfaces* / *Record/Playback* | Inversão de Controlo (IoC), Injeção de Dependências e Mocks para componentes externos. |