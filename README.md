# Warehouse Management

API REST desenvolvida em Java e Spring Boot para gerenciar ingredientes, estoque e compartimentos de um armazém.

## Pré-requisitos

- **Git** para clonar o repositório.
- **JDK 21** para compilar e executar o backend.
- **Docker** para executar o PostgreSQL 17 em um contêiner.
- **Docker Compose** com o comando `docker compose` para iniciar o banco com a configuração do projeto.

## Como executar a aplicação

1. Clone a branch `develop`, que contém a entrega, e entre na pasta do projeto.

   ```sh
   git clone --branch develop https://github.com/Incivius/warehouse-management.git
   cd warehouse-management
   ```

2. Na raiz do repositório, copie o arquivo de configuração.

   ```sh
   cp .env.example .env
   ```

   O `.env.example` já vem preenchido para execução local. Basta copiá-lo na primeira configuração. A senha do banco, a chave JWT e as credenciais iniciais da API são públicas e exclusivas para avaliação local. O Git ignora o arquivo `.env`.

3. Na raiz, inicie o PostgreSQL e verifique se ele está aceitando conexões.

   ```sh
   docker compose up -d postgres
   docker compose exec postgres sh -c 'pg_isready -h 127.0.0.1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
   ```

   Aguarde a mensagem `accepting connections` antes de prosseguir. Se a porta `5432` estiver ocupada, ajuste `DB_PORT` no `.env` antes de subir o banco. O Compose e o Spring Boot usam esse mesmo valor.

4. Entre em `backend` e inicie o Spring Boot com o Maven Wrapper.

   ```sh
   cd backend
   ./mvnw spring-boot:run
   ```

   Na inicialização, o Flyway executa automaticamente as migrations pendentes. Em um banco novo, elas criam o esquema e inserem os tipos de ingredientes, os cinco compartimentos e os usuários iniciais.

5. Acesse a API em `http://localhost:8080`. Use a requisição abaixo para confirmar que ela está funcionando.

## Primeiro acesso

- **Login** `admin`
- **Senha** `admin123`

Envie uma requisição `POST /auth/login` com o JSON abaixo em um segundo terminal ou pelo Postman.

```sh
curl -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"login":"admin","senha":"admin123"}'
```

A resposta traz o JWT em `accessToken`. Envie o cabeçalho `Authorization: Bearer <accessToken>` nas demais rotas. O campo `expiresIn` indica a validade em segundos, com padrão de 3.600 segundos. Faça login novamente quando o token expirar.

## Principais funcionalidades

- Cadastrar e consultar ingredientes, com opção de registrar estoque inicial no cadastro.
- Registrar entradas e saídas e consultar o estoque total por tipo.
- Consultar compartimentos disponíveis para armazenamento ou retirada.
- Consultar o histórico paginado de movimentações, com o usuário responsável.

## Regras dos compartimentos

O armazém tem cinco compartimentos, de C1 a C5. Cada um pode armazenar vários ingredientes do mesmo tipo, respeitando a capacidade total.

| Tipo | Capacidade por compartimento |
| --- | ---: |
| `DRY` | 600 kg |
| `LIQUID` | 500 L |
| `REFRIGERATED` | 400 kg |

- Ao esvaziar, o compartimento pode receber o mesmo tipo no mesmo dia. Outro tipo só pode entrar a partir do dia seguinte, pelo fuso da aplicação.
- Compartimentos bloqueados impedem movimentações.
- As quantidades devem ser positivas, com até três casas decimais. A saída é limitada pelo saldo do ingrediente no compartimento.

## Endpoints

| Método e rota | Uso |
| --- | --- |
| `POST /auth/login` | Recebe `login` e `senha` e emite o JWT. |
| `POST /ingredientes` | Recebe `nome` e `tipo`. Para incluir estoque inicial, envie também `quantidade` e `compartimentoId` juntos. |
| `GET /ingredientes` | Lista os ingredientes cadastrados. |
| `GET /ingredientes/{id}` | Consulta o ingrediente pelo ID. |
| `GET /ingredientes/volume` | Soma o estoque por tipo, inclusive os tipos com saldo zero. |
| `POST /estoque/entrada` | Recebe `ingredienteId`, `compartimentoId` e `quantidade` para registrar uma entrada. |
| `POST /estoque/saida` | Recebe os mesmos campos para registrar uma saída. |
| `GET /compartimentos/disponiveis?tipo=DRY&quantidade=25` | Lista os compartimentos compatíveis com capacidade livre suficiente. |
| `GET /compartimentos/disponiveis-para-venda?tipo=DRY` | Lista os compartimentos com saldo do tipo solicitado. |
| `GET /historico` | Lista as movimentações em `content` e os metadados de paginação em `page`. |

No histórico, `sortBy` aceita `date` ou `compartment` e `order` aceita `asc` ou `desc`. Os padrões são `sortBy=date`, `order=desc`, `page=0` e `size=20`. O tamanho da página pode variar de 1 a 100.

## Testes automatizados

Com o PostgreSQL ativo e o `.env` configurado, execute em `backend`.

```sh
./mvnw clean test
```

Os testes de integração usam o PostgreSQL e criam esquemas isolados. O usuário configurado no Compose tem permissão para criá-los.

## Postman

1. Importe a [coleção Warehouse Management](postman/warehouse-management.postman_collection.json) e o [ambiente local](postman/warehouse-local.postman_environment.json).
2. Selecione o ambiente **Warehouse - Local** e execute **Login (salva JWT automaticamente)**. O ambiente já contém as credenciais públicas de avaliação e a requisição salva o JWT na variável `token`.
3. Execute **Cadastrar ingrediente (sem estoque inicial)** para preencher `ingredientId`. Depois execute **Entrada de estoque** e **Saída de estoque**, nessa ordem. Os exemplos usam C1 (`compartmentId=1`) e dependem da capacidade e do saldo disponíveis.
