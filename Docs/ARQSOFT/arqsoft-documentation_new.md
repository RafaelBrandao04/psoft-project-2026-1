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

### Contexto do sistema

O LMS não suporta adequadamente **extensibilidade**, **configurabilidade** e **testabilidade** (enunciado ARQSOFT P1, 2026-2027). O *system-as-is* acima confirma isso concretamente:

- Não existe qualquer componente para fontes de informação bibliográfica plugáveis (Google Books, Open Library) — hoje só existe uma integração fixa e não relacionada (`external.service`, API Ninjas, para uma citação/facto do dia).
- Não existe nenhum mecanismo de notificação de leitores (email, SMS, webhook).
- As políticas de empréstimo estão *hardcoded* no código (`LendingServiceImpl`, valores fixos de duração e multa), sem seleção em runtime nem parametrização externa.
- A cobertura de testes automáticos é fraca nos controllers (ver análise ODSOFT) — compromete a testabilidade exigida.

### Problemas

- Falta de extensibilidade (novas fontes bibliográficas, novos mecanismos de notificação, novas políticas de empréstimo obrigam a alterar código existente).
- Falta de configurabilidade (não é possível escolher, à parametrização/arranque, quais fontes/mecanismos/políticas usar).
- Testabilidade insuficiente (poucos testes automáticos aos controllers e às regras de negócio).

### Requisitos Funcionais / Não Funcionais, Classificação dos Fatores Arquiteturais, ADD, System-to-be, Táticas, Arquiteturas de Referência, Padrões, Alternativas de Design

Pendente — são os passos seguintes naturais depois deste levantamento do *system-as-is*, seguindo a mesma estrutura do documento de referência.