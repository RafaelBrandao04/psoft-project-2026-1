# Relatório de Concretização e Decisões de Arquitetura — Issue #25

**Módulo:** ARQSOFT / Lending Management  
**Item / Tópico:** 1.3.3.3 Dynamic Lending Policies (Iteração 3 do ADD)  
**Autor:** Gonçalo Neto (`1260529@isep.ipp.pt`)  
**Data:** 25 de Outubro de 2026  
**Estado:** Concluído e Verificado (`BUILD SUCCESS`, todos os testes aprovados)

---

## 1. Contexto e Enquadramento da Issue #25

No âmbito da disciplina de **Arquitetura de Software (ARQSOFT)** e do processo de desenho **Attribute-Driven Design (ADD 3.0)**, a **Iteração 3** foca-se na instanciação e refinamento de módulos de domínio para satisfazer atributos de qualidade críticos, em particular **Modificabilidade (QA-MOD-*)**, **Extensibilidade** e **Manutenibilidade**.

### Problema Identificado no Sistema *As-Is*
No modelo anterior:
1. As regras de empréstimo (duração máxima em dias, número máximo de livros simultâneos e valor diário da multa por atraso) eram fixas e uniformes para todos os utilizadores e livros, lidas de propriedades globais estáticas (`library.properties`).
2. O serviço `LendingServiceImpl` aplicava validações rígidas com limites constantes (`maxNumberLendings = 3`), sem distinção entre:
   - Leitores com diferentes históricos e volumes de livros ativos;
   - Leitores comuns vs. Leitores Premium/Investigadores/Docentes;
   - Livros de circulação corrente vs. Livros de Referência / Leitura Curta / Obras Raras.
3. Qualquer alteração ou introdução de um novo tipo de política de negócio exigia alterar diretamente o código de `LendingServiceImpl` e da entidade `Lending`, violando o princípio **Open/Closed (OCP)** e o isolamento de táticas arquiteturais de modificabilidade.

### Objetivo da Issue #25
Desenvolver um motor de políticas de empréstimo dinâmico, extensível e configurável externamente, que:
- Permita avaliar em tempo de execução qual a política mais adequada para um determinado par `(Leitor, Livro)`.
- Suporte a regra de negócio escalonada (1º e 2º livros com período longo; 3º livro com período standard; 4º ou mais livros com período curto).
- Permita alterar períodos e criar novas políticas em tempo de execução (*runtime*) via API REST sem reiniciar a aplicação.
- Configure durações, limites de empréstimo e taxas de coima via ficheiros de configuração (`application.properties` e `library.properties`) sem recompilação de código.
- Mantenha total compatibilidade com os testes e funcionalidades existentes (zero regressão).

---

## 2. Decisões de Desenho e Arquitetura Adotadas

A solução implementada assenta na conjugação de três padrões de desenho e táticas de modificabilidade consagradas:

```
                            +-----------------------------------+
                            |     LendingPolicyProperties       |
                            | (@ConfigurationProperties prefix) |
                            +-----------------+-----------------+
                                              | injeta parâmetros
                                              v
+-----------------------+           +----------------------+
|  LendingServiceImpl   | --------> | LendingPolicyResolver|
+-----------------------+           +----------+-----------+
                                               |
                     +-------------------------+-------------------------+
                     |                         |                         |
                     v                         v                         v
          +---------------------+   +---------------------+   +---------------------+
          |StandardLendingPolicy|   | LongTermPolicy(30d) |   |ShortTermLendingPolic|
          |  (Prioridade 10)    |   |  (Prioridade 15)    |   |  (Prioridade 30)    |
          +---------------------+   +---------------------+   +---------------------+
```

### 2.1. Padrão Strategy (`LendingPolicy`)
- Foi definida a interface `LendingPolicy`, desacoplando a lógica de validação e parametrização do serviço orquestrador.
- Cada política encapsula o seu próprio critério de aplicabilidade (`isApplicable(Reader, Book, List<Lending>)`), a validação de regras de negócio (`validateCanLend(Reader, Book, List<Lending>)`), e os respetivos atributos de empréstimo:
  - `getLendingDurationInDays()`
  - `getMaxSimultaneousLendings()`
  - `getFineValuePerDayInCents()`
  - `getPriority()`

### 2.2. Padrão Intermediary / Policy Resolver (`LendingPolicyResolver`)
- O `LendingPolicyResolver` atua como um coordenador e repositório *thread-safe* (`CopyOnWriteArrayList<LendingPolicy>`) que gere todas as estratégias disponíveis.
- Quando um novo empréstimo é solicitado, o resolver filtra as políticas aplicáveis ao contexto e seleciona aquela com maior prioridade:
  $$\text{Selected Policy} = \arg\max_{p \in \text{Policies}, p.\text{isApplicable}(r, b, l)} p.\text{getPriority()}$$
- Se nenhuma política específica se qualificar, recorre de forma segura e transparente à `StandardLendingPolicy` (Default/Fallback).

### 2.3. Externalização da Configuração (`LendingPolicyProperties`)
- Através da anotação `@ConfigurationProperties(prefix = "lending.policies")`, todos os valores numéricos (dias, limites de empréstimo, valores de coima) são mapeados a partir do `application.properties` / `library.properties`.
- Tal cumpre a tática arquitetural **"Defer Binding Time"** (Diferir o Momento de Ligação): um administrador de sistemas pode ajustar os prazos ou multas sem tocar numa linha de código Java nem recompilar o artefacto JAR.

---

## 3. Análise de Alternativas Rejeitadas e Raciocínio Comparativo

Para fundamentar as opções tomadas, foram avaliadas três abordagens alternativas comumente encontradas na indústria:

### Alternativa A: Condicionais *Hardcoded* no `LendingServiceImpl` (`if/else` encadeados)
* **Como seria:** Adicionar verificações do tipo `if (activeCount < 2) ... else if (activeCount == 2) ...` diretamente dentro do método `create()` de `LendingServiceImpl`.
* **Por que foi rejeitada:**
  1. **Violação do Open/Closed Principle (OCP):** Cada nova política obrigaria a modificar `LendingServiceImpl`, aumentando o risco de introduzir regressões no fluxo principal.
  2. **Violação do Single Responsibility Principle (SRP):** O serviço passaria a misturar orquestração transacional com lógica granular de regras de negócio.
  3. **Baixa Testabilidade:** Impossibilitaria testar cada política isoladamente; qualquer teste teria de montar todo o contexto de mocks de repositórios e serviços.

### Alternativa B: Motor de Regras Externo (ex.: Drools / Easy Rules)
* **Como seria:** Importar o ecossistema Drools com ficheiros `.drl` compilados em tempo de execução para avaliar as políticas.
* **Por que foi rejeitada:**
  1. **Sobre-engenharia (Over-engineering):** O domínio da biblioteca apresenta cerca de 3 a 5 variações de políticas. Introduzir o Drools aumentaria o peso do pacote em dezenas de megabytes e adicionaria complexidade desnecessária.
  2. **Performance e Overhead:** A compilação e avaliação em grafo Rete/PHREAK de regras externas adiciona latência de CPU e consumo de memória irrelevante para este volume de dados.
  3. **Manutenção e Curva de Aprendizagem:** Dificultaria a integração contínua (CI/CD) e a depuração de código pela equipa.

### Alternativa C: Scripts Dinâmicos ou Tabelas de Base de Dados (ex.: Groovy scripts ou DB-driven rules)
* **Como seria:** Guardar scripts de regras numa tabela da base de dados e executá-los dinamicamente via Groovy/JavaScript engine.
* **Por que foi rejeitada:**
  1. **Riscos de Segurança:** Execução dinâmica de código introduz vulnerabilidades de *Code Injection*.
  2. **Perda de Tipagem Forte e Refatoração Segura:** O compilador Java deixa de garantir a consistência das chamadas de métodos e modelos de domínio.
  3. **Sobrecarga de I/O:** Cada validação de empréstimo exigiria queries à base de dados para descarregar o script da regra.

### Conclusão Comparativa
A abordagem selecionada (**Strategy + Resolver + Spring Configuration Properties + REST Admin Management**) é a mais acertada porque:
- **Atinge Modificabilidade Máxima:** Para adicionar uma nova política, basta registá-la via API em tempo de execução ou criar uma classe anotada com `@Component`. Nenhuma classe existente é alterada.
- **Mantém Alta Coesão e Baixo Acoplamento:** Cada classe tem apenas um motivo para mudar.
- **Possui Elevado Desempenho:** A resolução corre em memória com complexidade $O(N)$ sobre uma coleção de políticas.
- **É 100% Nativa do Spring Boot:** Aproveita a injeção de dependências sem necessidade de dependências externas pesadas.
