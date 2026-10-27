# Tutorial Passo a Passo: Como Testar as Políticas de Empréstimo no Swagger UI

**Projeto:** LMS (Library Management System) — ARQSOFT  
**Tópico:** 1.3.3.3 Dynamic Lending Policies (Issue #25)  
**Autor:** Gonçalo Neto  
**Data:** 25 de Outubro de 2026  

---

## 1. Como Iniciar o Servidor

1. Abre o terminal (PowerShell) na pasta raiz do projeto:  
   `c:\ISEP\ODSOFT\psoft-project-2026-1`
2. Executa o comando do Maven Wrapper para arrancar a aplicação:
   ```powershell
   .\mvnw.cmd spring-boot:run
   ```
3. Aguarda cerca de 10 a 15 segundos até veres na consola a mensagem de sucesso:
   ```text
   Started PsoftG1Application in X.XXX seconds
   ```
4. Abre o teu browser e acede ao Swagger UI:  
   👉 **[http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)**

---

## 2. Como Fazer a Autenticação no Swagger

Para poderes executar operações de administração e criação de empréstimos, precisas de te autenticar como **Bibliotecária / Admin** (`maria@gmail.com`).

Tens duas opções disponíveis no botão verde **"Authorize"** (no topo direito da página):

### Opção A (Mais Fácil e Rápida): HTTP Basic Auth
1. Clica no botão verde **"Authorize"** no topo direito.
2. Na janela que se abre, procura a secção **Basic Authentication (http, Basic)**.
3. Preenche os campos:
   * **Username:** `maria@gmail.com`
   * **Password:** `Mariaroberta!123`
4. Clica no botão **"Authorize"** e depois em **"Close"**.  
   *(O cadeado no topo fica agora fechado, o que significa que estás autenticado para todas as chamadas).*

---

### Opção B: Obter Token JWT (Bearer Token)
Se preferires autenticar através de Token JWT:
1. No Swagger, expande a secção **Authentication**.
2. Clica no endpoint **`POST /api/public/login`** e carrega no botão **"Try it out"**.
3. No corpo do pedido (*Request body*), coloca:
   ```json
   {
     \"username\": \"maria@gmail.com\",
     \"password\": \"Mariaroberta!123\"
   }
   ```
4. Clica no botão azul **"Execute"**.
5. Na secção da resposta (**Response headers**), procura o cabeçalho `authorization:` e copia a string longa do token (ex.: `eyJhbGciOi...`).
6. Clica no botão verde **"Authorize"** no topo, cola o token no campo **Bearer Authentication (`Value:`)** e clica em **"Authorize"**.

---

## 3. Teste 1: Consultar as Políticas Iniciais em Vigor

Vamos verificar que todas as políticas dinâmicas foram carregadas no arranque:

1. No Swagger, procura a secção **`Lending Policies Admin`**.
2. Clica no endpoint **`GET /api/admin/lending-policies`**.
3. Clica em **"Try it out"** e de seguida em **"Execute"**.
4. **O que deves observar na resposta (200 OK):**
   ```json
   [
     {
       "policyName": "LONG_TERM",
       "description": "Long-term policy (1st and 2nd books: 30 days)",
       "durationInDays": 30,
       "maxSimultaneousLendings": 5,
       "fineValuePerDayInCents": 100,
       "priority": 15
     },
     {
       "policyName": "STANDARD",
       "description": "Standard policy (baseline / 3rd book: 15 days)",
       "durationInDays": 15,
       "maxSimultaneousLendings": 5,
       "fineValuePerDayInCents": 200,
       "priority": 10
     },
     {
       "policyName": "SHORT_TERM",
       "description": "Short-term policy (4th+ books or reference books: 7 days)",
       "durationInDays": 7,
       "maxSimultaneousLendings": 5,
       "fineValuePerDayInCents": 400,
       "priority": 30
     },
     {
       "policyName": "PREMIUM",
       "description": "Premium reader policy (researchers/professors: 30 days)",
       "durationInDays": 30,
       "maxSimultaneousLendings": 5,
       "fineValuePerDayInCents": 100,
       "priority": 20
     }
   ]
   ```

---

## 4. Teste 2: Testar a Regra dos Prazos Escalonados

Vamos usar a leitora **Catarina Martins** (Leitor Nº **`2026/4`**), que arranca sem livros alugados, e requisitar livros sucessivos para comprovar a regra de negócio:

> **Regra:**  
> • Livros 1 e 2 $\rightarrow$ **30 dias** (Período Longo)  
> • Livro 3 $\rightarrow$ **15 dias** (Período Standard) — *os dois anteriores mantêm os 30 dias*  
> • Livros 4 e 5 $\rightarrow$ **7 dias** (Período Curto)  
> • Livro 6 $\rightarrow$ **Bloqueado** (Excede limite máximo de 5 livros)

No Swagger, vai à secção **`Lendings`** $\rightarrow$ **`POST /api/lendings`** $\rightarrow$ clica em **"Try it out"**:

### Passo 2.1: Requisitar o 1º Livro (Prazo de 30 dias)
* No Request body, coloca:
  ```json
  {
    "isbn": "9789720706386",
    "readerNumber": "2026/4"
  }
  ```
* Clica em **"Execute"**.
* **Resultado:** Status **201 Created**.
* Repara no campo **`limitDate`**: a data é exatamente **hoje + 30 dias** (aplicou `LONG_TERM`).

---

### Passo 2.2: Requisitar o 2º Livro (Prazo de 30 dias)
* No Request body, coloca outro livro existente:
  ```json
  {
    "isbn": "9789723716160",
    "readerNumber": "2026/4"
  }
  ```
* Clica em **"Execute"**.
* **Resultado:** Status **201 Created**.
* Repara no campo **`limitDate`**: continua a ter **+30 dias** (a leitora ainda só tinha 1 livro ativo, logo este 2º livro também beneficia de `LONG_TERM`).

---

### Passo 2.3: Requisitar o 3º Livro (Prazo Standard de 15 dias)
* No Request body, coloca o 3º livro:
  ```json
  {
    "isbn": "9789895612864",
    "readerNumber": "2026/4"
  }
  ```
* Clica em **"Execute"**.
* **Resultado:** Status **201 Created**.
* Repara no campo **`limitDate`**: agora a data é **hoje + 15 dias** (transitou para `STANDARD`).  
  *(Os 2 livros requisitados anteriormente mantêm os seus 30 dias intactos!).*

---

### Passo 2.4: Requisitar o 4º Livro (Prazo Curto de 7 dias)
* No Request body, coloca o 4º livro:
  ```json
  {
    "isbn": "9782722203402",
    "readerNumber": "2026/4"
  }
  ```
* Clica em **"Execute"**.
* **Resultado:** Status **201 Created**.
* Repara no campo **`limitDate`**: agora a data é **hoje + 7 dias** (transitou para `SHORT_TERM` porque o leitor já acumulou $\ge 3$ livros).
* O campo `fineValuePerDayInCents` subiu para **400** cêntimos (multa agravada).

---

### Passo 2.5: Requisitar o 5º Livro (Prazo Curto de 7 dias)
* No Request body:
  ```json
  {
    "isbn": "9789722328296",
    "readerNumber": "2026/4"
  }
  ```
* Clica em **"Execute"**.
* **Resultado:** Status **201 Created** com **+7 dias**.

---

### Passo 2.6: Tentar Requisitar um 6º Livro (Rejeição por Limite Máximo)
* No Request body, tenta requisitar outro livro (ex.: `"9789720046710"`).
* Clica em **"Execute"**.
* **Resultado:** Status **403 Forbidden**.
* Mensagem de erro:
  ```text
  Reader has reached the maximum number of simultaneous lendings (5)
  ```
  *(Provando que a validação de segurança e fronteira de negócio está a funcionar a 100%).*

---

## 5. Teste 3: Alterar a Duração de um Período em *Runtime* (sem reiniciar a aplicação)

Imagina que o Administrador da biblioteca decide que o **Período Longo** deve passar de 30 para **45 dias**:

1. No Swagger, vai a **`Lending Policies Admin`** $\rightarrow$ **`PUT /api/admin/lending-policies/{policyName}`**.
2. Clica em **"Try it out"**.
3. No campo `policyName`, escreve:
   ```text
   LONG_TERM
   ```
4. No Request body, coloca:
   ```json
   {
     "durationInDays": 45,
     "maxSimultaneousLendings": 5,
     "fineValuePerDayInCents": 100
   }
   ```
5. Clica em **"Execute"**.
6. **Resultado:** Status **200 OK**. A resposta confirma `"durationInDays": 45`.

#### Verificação Imediata da Alteração:
1. Volta a **`POST /api/lendings`**.
2. Requisita um livro para um leitor que ainda não tenha livros (ex.: `readerNumber`: `"2026/7"` — *António Costa*):
   ```json
   {
     "isbn": "9789720046710",
     "readerNumber": "2026/7"
   }
   ```
3. Clica em **"Execute"**.
4. Repara no campo **`limitDate`**: agora a data calculada tem **+45 dias**!  
   *Isto prova que a alteração em tempo de execução funcionou de forma imediata sem necessidade de reiniciar o servidor.*

---

## 6. Teste 4: Criar uma Nova Política Dinâmica em *Runtime*

Vamos criar uma nova política de empréstimo de raiz diretamente pelo Swagger:

1. No Swagger, vai a **`Lending Policies Admin`** $\rightarrow$ **`POST /api/admin/lending-policies`**.
2. Clica em **"Try it out"**.
3. No Request body, coloca:
   ```json
   {
     "policyName": "REGIME_VERAO",
     "description": "Emprestimos alargados para ferias de verao",
     "durationInDays": 60,
     "maxSimultaneousLendings": 5,
     "fineValuePerDayInCents": 50,
     "priority": 50,
     "minActiveLendings": 0,
     "maxActiveLendings": 5,
     "readerTypeKeyword": null,
     "bookGenre": null
   }
   ```
4. Clica em **"Execute"**.
5. **Resultado:** Status **201 Created**.
6. Executa novamente o `GET /api/admin/lending-policies`: a nova regra `REGIME_VERAO` já aparece registada.
7. Como tem prioridade **50** (superior a 15 e 10), qualquer novo empréstimo passará a receber automaticamente **60 dias** de prazo.

---

## 7. Tabela de Apoio para Testes (Dados do Bootstrap)

### Credenciais
| Papel | Username | Password |
| :--- | :--- | :--- |
| **Bibliotecária (Admin)** | `maria@gmail.com` | `Mariaroberta!123` |

### Leitores Disponíveis
| Leitor | Username | Reader Number |
| :--- | :--- | :--- |
| Catarina Martins | `catarina@gmail.com` | `2026/4` |
| António Costa | `antonio@gmail.com` | `2026/7` |
| André Ventura | `andre@gmail.com` | `2026/8` |
| Manuel Sarapinto | `manuel@gmail.com` | `2026/1` |

### ISBNs de Livros Válidos no Sistema
| Título da Obra | ISBN |
| :--- | :--- |
| Ensaio sobre a Cegueira | `9789720706386` |
| Memorial do Convento | `9789723716160` |
| O Hobbit | `9789895612864` |
| Asterix o Gaulês | `9782722203402` |
| Harry Potter e a Pedra Filosofal | `9789722328296` |
| Os Maias | `9789720046710` |
