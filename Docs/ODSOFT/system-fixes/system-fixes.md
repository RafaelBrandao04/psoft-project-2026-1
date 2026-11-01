# Correções do Sistema e Estabilização da Linha de Base

## 1. Correções de Execução e Ambiente

### 1.1. Infraestrutura do Maven Wrapper
- **Problema:** Os scripts locais do Maven wrapper (`mvnw` / `mvnw.cmd`) careciam do diretório `.mvn/wrapper/` e do ficheiro `maven-wrapper.properties`, impedindo compilações em máquinas limpas ou executores de CI sem um runtime Maven pré-instalado globalmente.
- **Resolução:** Regenerou-se a configuração do wrapper através de `mvn wrapper:wrapper`, fixando a versão do Maven na `3.9.9` com resolução autónoma de bootstrap.
- **Impacto:** Restabelece a portabilidade e reprodutibilidade do build em ambientes de desenvolvimento heterogéneos e executores automatizados de CI/CD.

### 1.2. Base de Dados e Execução Autónoma
- **Problema:** O empacotamento era bem-sucedido (`mvn clean package`), mas a execução do JAR autónomo (`java -jar target/psoft-g1-0.0.1-SNAPSHOT.jar`) falhava com `HibernateException: Unable to determine Dialect`. A configuração padrão em `application.properties` dependia de um processo externo servidor H2 TCP (`jdbc:h2:tcp://localhost/~/psoft-g1;IGNORECASE=TRUE`). Os testes unitários (`mvn test`) não detetavam esta falha porque o Spring Boot Test substituía automaticamente a fonte de dados por uma instância em memória.
- **Resolução:** Atualizou-se `application.properties` para adotar por omissão uma base de dados autónoma em memória:
  `spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;IGNORECASE=TRUE`
- **Impacto:** Elimina a dependência de base de dados externa, viabilizando a execução autónoma imediata e a portabilidade da aplicação.

### 1.3. Sincronização do Povoamento Inicial de Dados (Bootstrap)
- **Problema:** A inicialização pós-arranque falhava com `IndexOutOfBoundsException: Index 0 out of bounds for length 0` em `Bootstrapper.java`. Enquanto o `UserBootstrapper.java` gerava números de leitor dinamicamente usando o ano civil corrente (`LocalDate.now().getYear()` -> `2026/1`, `2026/2`), o `Bootstrapper.java` procurava strings fixas hardcoded de `"2024/*"`. Em 2026, nenhum leitor era encontrado e a geração de empréstimos falhava.
- **Resolução:** Atualizou-se a pesquisa de leitores em `Bootstrapper.java` para consultar dinamicamente com base no ano corrente com recurso a `2024` como fallback, adicionando verificações defensivas de limites antes de instanciar entidades de empréstimo.
- **Impacto:** Garante a estabilidade do arranque da aplicação e a consistência dos dados de teste independentemente do ano de execução.

### 1.4. Segurança da Consola H2 e Acessibilidade de Frame Options
- **Problema:** O acesso à consola web da base de dados H2 (`http://localhost:8080/h2-console`) durante a execução local devolvia `401 Unauthorized`. Em `SecurityConfig.java`, embora as rotas do Swagger e da API pública estivessem autorizadas, o servlet da consola H2 (`/h2-console/**`) não tinha regras explícitas de permissão, caindo na regra `.anyRequest().authenticated()`. Adicionalmente, o cabeçalho padrão `X-Frame-Options: DENY` do Spring Security bloqueava a renderização dos iframes da consola.
- **Resolução:** Em `SecurityConfig.java`:
  1. Configurou-se `.requestMatchers(AntPathRequestMatcher.antMatcher("/h2-console/**")).permitAll()` para permitir acesso em desenvolvimento sem desencadear conflitos de introspeção de rotas do Spring MVC.
  2. Configurou-se `headers.frameOptions(frameOptions -> frameOptions.sameOrigin())` para permitir a renderização de iframes na consola web do H2.
- **Impacto:** Permite o acesso à interface da base de dados em memória para inspeção e depuração de dados durante o desenvolvimento, mantendo a segurança estrita nos endpoints REST protegidos.

---

## 2. Integração de Testes de Mutação

- **Limitação da Linha de Base:** Embora a documentação inicial referisse métricas de cobertura de mutação, os testes de mutação não estavam integrados no ciclo de build do Maven. A configuração do PITest estava ausente do `pom.xml`.
- **Configuração:** Integrou-se o `pitest-maven` (v1.15.8) e o `pitest-junit5-plugin` (v1.2.1) sob `<build><plugins>` no `pom.xml`, configurando o pacote alvo `pt.psoft.g1.psoftg1.*`, multithreading (4 threads) e formatos de saída (HTML e XML).
- **Execução:** Executável via `mvn test-compile pitest:mutationCoverage` ou através do perfil dedicado de mutação.

---

## 3. Correção de Defeitos de Análise Estática (SpotBugs SAST)

Durante a integração da análise estática de código (`spotbugs-maven-plugin:4.8.3.1`) no pipeline de compilação, a análise de bytecode identificou 3 defeitos reais de Alta Prioridade (Prioridade 1) no código-fonte original:

### 3.1. Vulnerabilidade de Codificação de Caracteres (`DM_DEFAULT_ENCODING`)
- **Localização:** `src/main/java/pt/psoft/g1/psoftg1/shared/services/ForbiddenNameServiceImpl.java` (linha 21).
- **Problema:** A instanciação `new InputStreamReader(resource.getInputStream())` dependia implicitamente da codificação de caracteres padrão do sistema operativo anfitrião. Em máquinas de desenvolvimento Windows operando sob `Cp1252` ou `Windows-1252`, nomes proibidos portugueses com caracteres não-ASCII eram corrompidos, divergindo dos executores Linux de CI que operam em `UTF-8`.
- **Resolução:** Declarou-se explicitamente `StandardCharsets.UTF_8`:
  ```java
  try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)))
  ```
- **Impacto:** Elimina divergências ambientais de codificação e garante o tratamento uniforme de texto Unicode em todos os sistemas operativos.

### 3.2. Atribuição de Variável Local Não Utilizada (`DLS_DEAD_LOCAL_STORE`)
- **Localização:** `src/main/java/pt/psoft/g1/psoftg1/usermanagement/model/User.java` (linha 166).
- **Problema:** No método `setPassword(String)`, o código instanciava `new Password(password)` estritamente para validar o formato da palavra-passe, mas armazenava o resultado numa variável local nunca lida: `Password passwordCheck = new Password(password);`.
- **Resolução:** Substituiu-se a atribuição morta pela invocação direta de validação:
  ```java
  public void setPassword(final String password) {
      new Password(password);
      final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
      this.password = passwordEncoder.encode(password);
  }
  ```
- **Impacto:** Elimina alocação de memória desnecessária e remove o aviso da análise estática sem alterar a lógica de validação de domínio.

### 3.3. Falha de Integridade na Serialização (`SE_BAD_FIELD`)
- **Localização:** `src/main/java/pt/psoft/g1/psoftg1/shared/model/Name.java` e `pt/psoft/g1/psoftg1/lendingmanagement/api/FineView.java`.
- **Problema:** 
  1. A entidade `User` implementa `UserDetails` do Spring Security, que estende `java.io.Serializable`. Contudo, o Value Object embutido `Name` (`private Name name;`) não implementava `Serializable`. Caso tokens de autenticação, sessões HTTP ou contextos de segurança fossem serializados, ocorria uma exceção `java.io.NotSerializableException` em runtime.
  2. A classe `FineView` estava marcada com `implements Serializable`, enquanto a sua composição filha `LendingView` não estava, gerando inconsistências na serialização de DTOs.
- **Resolução:** 
  1. Adicionou-se `implements java.io.Serializable` e o campo explícito `private static final long serialVersionUID = 1L;` em `Name.java`.
  2. Removeu-se o marcador redundante `implements Serializable` de `FineView.java` para alinhar com os restantes DTOs de visualização REST.
- **Impacto:** Garante a integridade da serialização de sessões do Spring Security e estabilidade em runtime.
