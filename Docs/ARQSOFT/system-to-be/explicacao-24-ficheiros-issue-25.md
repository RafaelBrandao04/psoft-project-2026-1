# Inventário e Explicação Detalhada Ficheiro a Ficheiro — Issue #25

**Módulo:** ARQSOFT / LMS (Library Management System)  
**Tópico do Enunciado:** 1.3.3.3 Lending Policies & 1.3.3.4 Automated Functional Testing  
**Responsável:** Gonçalo Neto (`1260529@isep.ipp.pt`)  
**Data:** 25 de Outubro de 2026  
**Total de Ficheiros Envolvidos:** 24 ficheiros (18 novos/modificados de código Java, 2 ficheiros de propriedades e 4 documentos/relatórios)  
**Estado da Validação:** `BUILD SUCCESS` (Todos os testes aprovados)

---

## 1. Visão Geral das Alterações

Para responder integralmente aos requisitos da **Issue #25**, aos objetivos da **Iteração 3 do ADD 3.0** e à regra de negócio de prazos escalonados e gestão em *runtime*, foram criados e ajustados os ficheiros da arquitetura de políticas de empréstimo.

Abaixo encontra-se a explicação individual, **ficheiro a ficheiro**, descrevendo **o que cada um é**, **de que se trata** e a **razão arquitetural pela qual foi criado ou alterado**.

---

## 2. Detalhe Ficheiro a Ficheiro

### Grupo 1: Núcleo de Políticas de Domínio (Padrão Strategy)
*Localização:* `src/main/java/pt/psoft/g1/psoftg1/lendingmanagement/policies/`

#### 1. `LendingPolicy.java`
* **Caminho:** `src/main/java/pt/psoft/g1/psoftg1/lendingmanagement/policies/LendingPolicy.java`
* **O que é:** Interface Java que estabelece o contrato formal do padrão de desenho **Strategy** para o domínio de políticas de empréstimo.
* **De que se trata:** Define as assinaturas dos métodos fundamentais que qualquer política deve cumprir (`isApplicable`, `validateCanLend`, getters e setters dinâmicos).
* **Por que foi criado:** Permite desacoplar as regras de negócio de empréstimo do serviço orquestrador `LendingServiceImpl`. Garante a conformidade com o princípio **Open/Closed (OCP)**.

---

#### 2. `LongTermLendingPolicy.java`
* **Caminho:** `src/main/java/pt/psoft/g1/psoftg1/lendingmanagement/policies/LongTermLendingPolicy.java`
* **O que é:** Classe Spring Component (`@Component`) que implementa a interface `LendingPolicy`.
* **De que se trata:** Modela a política de **Período Longo** (por omissão **30 dias**, coima diária de 100 cêntimos e prioridade 15).
* **Por que foi criado:** Concretiza a regra do **1º e 2º livros**: quando o leitor tem $\le 1$ livros alugados, o método `isApplicable` devolve `true`, atribuindo aos dois primeiros livros o período longo de 30 dias.

---

#### 3. `StandardLendingPolicy.java`
* **Caminho:** `src/main/java/pt/psoft/g1/psoftg1/lendingmanagement/policies/StandardLendingPolicy.java`
* **O que é:** Classe Spring Component (`@Component`) que implementa a interface `LendingPolicy`.
* **De que se trata:** Modela a política de **Período Standard** (por omissão **15 dias**, coima diária de 200 cêntimos e prioridade 10).
* **Por que foi criado:** Concretiza a regra do **3º livro**: quando o leitor já possui 2 livros alugados em sua posse e pede o 3º, esta política é ativada. Atua também como política de contingência (*fallback*) universal.

---

#### 4. `ShortTermLendingPolicy.java`
* **Caminho:** `src/main/java/pt/psoft/g1/psoftg1/lendingmanagement/policies/ShortTermLendingPolicy.java`
* **O que é:** Classe Spring Component (`@Component`) que implementa a interface `LendingPolicy`.
* **De que se trata:** Modela a política de **Período Curto** (por omissão **7 dias**, coima diária de 400 cêntimos).
* **Por que foi criado:** Concretiza a regra do **4º ou mais livros**: quando o leitor já tem $\ge 3$ livros ativos, qualquer livro subsequente recebe o período curto de 7 dias com multa agravada. Aplica-se igualmente a livros de referência.

---

#### 5. `PremiumReaderLendingPolicy.java`
* **Caminho:** `src/main/java/pt/psoft/g1/psoftg1/lendingmanagement/policies/PremiumReaderLendingPolicy.java`
* **O que é:** Classe Spring Component (`@Component`) que implementa a interface `LendingPolicy`.
* **De que se trata:** Modela uma política especial com bonificação para leitores diferenciados (professores, investigadores e leitores VIP), concedendo 30 dias e prioridade 20.
* **Por que foi criado:** Dá resposta aos requisitos de perfil de leitor definidos na documentação arquitetural da Iteração 3 do ADD.

---

#### 6. `DynamicLendingPolicy.java`
* **Caminho:** `src/main/java/pt/psoft/g1/psoftg1/lendingmanagement/policies/DynamicLendingPolicy.java`
* **O que é:** Classe Java instanciável em tempo de execução (*runtime*) que implementa `LendingPolicy`.
* **De que se trata:** Armazena parâmetros e regras configuradas dinamicamente: nome da regra, duração em dias, limite de livros, coima, prioridade, e critérios opcionais de filtragem.
* **Por que foi criado:** Satisfaz diretamente o requisito: *"ter a possibilidade de criar novas lending policies em run time"*.

---

#### 7. `LendingPolicyResolver.java`
* **Caminho:** `src/main/java/pt/psoft/g1/psoftg1/lendingmanagement/policies/LendingPolicyResolver.java`
* **O que é:** Componente Spring (`@Component`) que atua como **Policy Engine** e **Dynamic Registry**.
* **De que se trata:** Mantém uma coleção *thread-safe* (`CopyOnWriteArrayList<LendingPolicy>`) com todas as estratégias disponíveis. Suporta métodos dinâmicos de gestão: `updatePolicy(...)`, `registerPolicy(...)` e `removePolicy(...)`.
* **Por que foi criado:** Ponto central de coordenação do padrão Strategy para selecionar em tempo de execução a política aplicável com maior prioridade.

---

### Grupo 2: API REST de Administração em Runtime
*Localização:* `src/main/java/pt/psoft/g1/psoftg1/lendingmanagement/api/`

#### 8. `LendingPolicyAdminController.java`
* **Caminho:** `src/main/java/pt/psoft/g1/psoftg1/lendingmanagement/api/LendingPolicyAdminController.java`
* **O que é:** Controlador Spring MVC (`@RestController`) anotado com OpenAPI/Swagger (`@Tag`), mapeado na URI `/api/admin/lending-policies`.
* **De que se trata:** Expõe endpoints HTTP REST (`GET`, `PUT`, `POST`, `DELETE`) para administradores e bibliotecários gerirem as políticas em runtime.
* **Por que foi criado:** Fornece a interface administrativa que permite aos gestores do LMS editar prazos e criar novas regras de empréstimo sem reiniciar o servidor.

---

#### 9. `LendingPolicyView.java`
* **Caminho:** `src/main/java/pt/psoft/g1/psoftg1/lendingmanagement/api/LendingPolicyView.java`
* **O que é:** DTO (Data Transfer Object) de saída da camada de apresentação.
* **Por que foi criado:** Preserva o isolamento de camadas, impedindo que as classes de domínio sejam serializadas diretamente para HTTP.

---

#### 10. `UpdateLendingPolicyRequest.java`
* **Caminho:** `src/main/java/pt/psoft/g1/psoftg1/lendingmanagement/api/UpdateLendingPolicyRequest.java`
* **O que é:** DTO de entrada para o endpoint `PUT /api/admin/lending-policies/{name}`.
* **Por que foi criado:** Assegura a validação estrita dos dados introduzidos pelo utilizador (`@Positive`), rejeitando valores inválidos antes de atingir o domínio.

---

#### 11. `CreateDynamicPolicyRequest.java`
* **Caminho:** `src/main/java/pt/psoft/g1/psoftg1/lendingmanagement/api/CreateDynamicPolicyRequest.java`
* **O que é:** DTO de entrada para o endpoint `POST /api/admin/lending-policies`.
* **Por que foi criado:** Garante uma receção tipada e segura dos pedidos de criação de novas políticas em tempo de execução.

---

### Grupo 3: Integração no Sistema e Configuração

#### 12. `LendingPolicyProperties.java`
* **Caminho:** `src/main/java/pt/psoft/g1/psoftg1/configuration/LendingPolicyProperties.java`
* **O que é:** Classe de configuração Spring Boot anotada com `@ConfigurationProperties(prefix = "lending.policies")`.
* **Por que foi criado:** Concretiza a tática arquitetural *\"Defer Binding Time\"* em *setup time* (ASR02).

---

#### 13. `LendingServiceImpl.java` *(Modificado)*
* **Caminho:** `src/main/java/pt/psoft/g1/psoftg1/lendingmanagement/services/LendingServiceImpl.java`
* **O que é:** Classe de serviço de aplicação do subdomínio de empréstimos.
* **Por que foi alterado:** Delega a determinação da duração e coima no método `create()` ao `LendingPolicyResolver`, preservando a publicação de eventos de domínio e garantindo retrocompatibilidade.

---

#### 14. `SecurityConfig.java` *(Modificado)*
* **Caminho:** `src/main/java/pt/psoft/g1/psoftg1/configuration/SecurityConfig.java`
* **Por que foi alterado:** Protege `/api/admin/lending-policies/**` para perfis com permissão `ADMIN` e `LIBRARIAN`.

---

#### 15. `ApiConfig.java` *(Modificado)*
* **Caminho:** `src/main/java/pt/psoft/g1/psoftg1/configuration/ApiConfig.java`
* **Por que foi alterado:** Configura esquemas OpenAPI para suportar tanto HTTP Basic Auth como Bearer Token JWT no Swagger UI.

---

#### 16. `application.properties` e `library.properties` *(Modificados)*
* **Caminho:** `src/main/resources/application.properties` e `src/main/resources/config/library.properties`
* **Por que foram alterados:** Definem os parâmetros padrão das políticas em arranque (*setup time*).

---

### Grupo 4: Testes Unitários e Integração
*Localização:* `src/test/java/pt/psoft/g1/psoftg1/lendingmanagement/policies/`

* `StandardLendingPolicyTest.java`: Análise de valores limite (BVA) e contingência.
* `LongTermLendingPolicyTest.java`: Testes para 0 e 1 livros (período longo).
* `ShortTermLendingPolicyTest.java`: Testes para 3+ livros e obras de referência (período curto).
* `PremiumReaderLendingPolicyTest.java`: Testes para perfil docente/investigador.
* `LendingPolicyResolverTest.java`: Testes de escalonamento dinâmico, edição e criação em runtime.
* `LendingPolicyAdminControllerTest.java`: Testes aos endpoints REST (`GET`, `PUT`, `POST`, `DELETE`).

---

### Grupo 5: Documentação
* `issue-25-completion-report.md`
* `apresentacao-grupo-issue-25.md`
* `explicacao-24-ficheiros-issue-25.md`
* `tutorial-testar-swagger-issue-25.md`
