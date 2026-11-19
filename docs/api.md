# Referência da API

A URL local padrão é `http://localhost:8080`. Envie `Content-Type: application/json` nas requisições com corpo. O login é público. Nas demais rotas, envie `Authorization: Bearer <accessToken>`. Os exemplos usam IDs ilustrativos.

- [Autenticação](#autenticação)
- [Ingredientes](#ingredientes)
- [Estoque](#estoque)
- [Compartimentos](#compartimentos)
- [Histórico e paginação](#histórico)
- [Valores aceitos](#valores-aceitos)
- [Respostas de erro](#respostas-de-erro)

## Valores aceitos

Use as grafias da tabela nos campos e parâmetros de tipo de ingrediente.

| Tipo | Categoria | Unidade |
| --- | --- | --- |
| `DRY` | Seco | `KG` (quilogramas) |
| `LIQUID` | Líquido | `L` (litros) |
| `REFRIGERATED` | Refrigerado | `KG` (quilogramas) |

As operações de estoque são `INBOUND` para entrada e `OUTBOUND` para saída. A API define a operação pela rota chamada e inclui o valor na resposta.

Nas requisições, envie quantidades como números decimais a partir de `0.001`, com até nove dígitos inteiros e três casas decimais. Use ponto como separador decimal. A unidade depende do tipo de ingrediente.

Os IDs são inteiros de 64 bits. Os campos `dataHora` e `timestamp` são strings ISO 8601 de data e hora local, sem offset de fuso.

## Respostas de erro

Em todas as rotas protegidas, a ausência de autenticação válida retorna `401 UNAUTHORIZED`. Exceções inesperadas tratadas pelo MVC retornam `500 INTERNAL_ERROR`. As seções abaixo listam os demais erros de cada operação.

```json
{
  "code": "INGREDIENT_NOT_FOUND",
  "message": "Ingrediente não encontrado.",
  "status": 404,
  "timestamp": "2026-11-18T14:00:00"
}
```

Falhas de validação usam `400 INVALID_REQUEST`. A mensagem pode trazer os detalhes dos campos inválidos.

| Campo | Tipo | Descrição |
| --- | --- | --- |
| `code` | string | Código de erro da API. |
| `message` | string | Mensagem legível para quem chamou a API. |
| `status` | inteiro | Código HTTP. |
| `timestamp` | string | Data e hora em que a resposta de erro foi gerada. |

## Autenticação

### `POST /auth/login`

Autentica o usuário e emite um JWT.

**Payload JSON**

| Campo | Tipo | Obrigatório | Validação |
| --- | --- | --- | --- |
| `login` | string | Sim | Login do usuário. Deve conter caracteres além de espaços. |
| `senha` | string | Sim | Senha do usuário. Deve conter caracteres além de espaços. |

```json
{"login":"admin","senha":"admin123"}
```

**Resposta · `200 OK`**

| Campo | Tipo | Descrição |
| --- | --- | --- |
| `accessToken` | string | JWT para enviar no cabeçalho `Authorization`. |
| `tokenType` | string | Sempre `Bearer`. |
| `expiresIn` | inteiro | Validade em segundos. O padrão é `3600`. |
| `usuario` | objeto | Usuário autenticado, com `id` (inteiro), `nome` (string) e `login` (string). |

```json
{
  "accessToken": "<JWT emitido>",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "usuario": {"id":1,"nome":"Administrador","login":"admin"}
}
```

As credenciais acima são públicas e exclusivas para avaliação local.

**Erros** — `400 INVALID_REQUEST` para corpo inválido. `401 INVALID_CREDENTIALS` para login ou senha incorretos.

## Ingredientes

O modelo de ingrediente retornado pelas consultas também compõe a resposta de cadastro.

| Campo | Tipo | Descrição |
| --- | --- | --- |
| `id` | inteiro | ID do ingrediente. |
| `nome` | string | Nome cadastrado. |
| `tipo` | string | Tipo de ingrediente. Consulte os [valores aceitos](#valores-aceitos). |

### `POST /ingredientes`

Cadastra um ingrediente, com opção de registrar uma entrada inicial no estoque.

**Payload JSON**

| Campo | Tipo | Obrigatório | Validação |
| --- | --- | --- | --- |
| `nome` | string | Sim | Deve conter caracteres além de espaços e ter até 150 caracteres. |
| `tipo` | string | Sim | `DRY`, `LIQUID` ou `REFRIGERATED`. |
| `quantidade` | decimal | Com estoque inicial | Quantidade positiva a partir de `0.001`, com até nove dígitos inteiros e três casas decimais. |
| `compartimentoId` | inteiro | Com estoque inicial | ID positivo de um compartimento existente. |

Para registrar estoque inicial, preencha `quantidade` e `compartimentoId` juntos. No cadastro simples, envie apenas `nome` e `tipo`.

```json
{"nome":"Arroz","tipo":"DRY","quantidade":12.125,"compartimentoId":1}
```

**Resposta · `201 Created`**

Além de `id`, `nome` e `tipo` do ingrediente, a resposta inclui os campos abaixo. No cadastro simples, os três retornam `null`.

| Campo | Tipo | Descrição |
| --- | --- | --- |
| `quantidade` | decimal ou `null` | Quantidade adicionada ao estoque. |
| `compartimento` | objeto ou `null` | Destino da entrada, com `id` (inteiro) e `codigo` (string). |
| `movimentacao` | objeto ou `null` | Entrada inicial, com `id` (inteiro), `tipo` (string, sempre `INBOUND`), `quantidade` (decimal), `responsavel` (string com o login) e `dataHora` (string). |

```json
{
  "id": 1,
  "nome": "Arroz",
  "tipo": "DRY",
  "quantidade": 12.125,
  "compartimento": {"id":1,"codigo":"C1"},
  "movimentacao": {
    "id": 1,
    "tipo": "INBOUND",
    "quantidade": 12.125,
    "responsavel": "admin",
    "dataHora": "2026-11-18T14:00:00"
  }
}
```

**Erros** — `400 INVALID_REQUEST` para dados inválidos. A entrada inicial pode retornar `400 INSUFFICIENT_CAPACITY`, `400 INCOMPATIBLE_INGREDIENT_TYPE`, `400 COMPARTMENT_TEMPORARILY_BLOCKED` ou `404 COMPARTMENT_NOT_FOUND`.

### `GET /ingredientes`

Lista todos os ingredientes em ordem crescente de ID. Faça a requisição sem corpo ou parâmetros.

**Resposta · `200 OK`**

Array de [ingredientes](#ingredientes). Se o cadastro estiver vazio, retorna `[]`.

```json
[{"id":1,"nome":"Arroz","tipo":"DRY"}]
```

**Erros** — Aplica os erros comuns das rotas protegidas.

### `GET /ingredientes/{id}`

Consulta um ingrediente pelo ID.

| Parâmetro de caminho | Tipo | Obrigatório | Descrição |
| --- | --- | --- | --- |
| `id` | inteiro | Sim | ID do ingrediente. Exemplo de chamada `GET /ingredientes/1`. |

**Resposta · `200 OK`**

Objeto com os campos do [ingrediente](#ingredientes).

```json
{"id":1,"nome":"Arroz","tipo":"DRY"}
```

**Erros** — `400 INVALID_REQUEST` se o ID não puder ser convertido. `404 INGREDIENT_NOT_FOUND` se o ingrediente não existir.

### `GET /ingredientes/volume`

Soma o estoque atual por tipo de ingrediente, incluindo tipos com saldo zero. Faça a requisição sem corpo ou parâmetros.

**Resposta · `200 OK`**

Array com um objeto por tipo, em ordem alfabética de `tipo`.

| Campo | Tipo | Descrição |
| --- | --- | --- |
| `tipo` | string | Tipo de ingrediente. Consulte os [valores aceitos](#valores-aceitos). |
| `quantidadeTotal` | decimal | Soma do saldo do tipo em todos os compartimentos. |
| `unidadeMedida` | string | `KG` ou `L`, conforme o tipo. |

```json
[
  {"tipo":"DRY","quantidadeTotal":12.125,"unidadeMedida":"KG"},
  {"tipo":"LIQUID","quantidadeTotal":0,"unidadeMedida":"L"},
  {"tipo":"REFRIGERATED","quantidadeTotal":0,"unidadeMedida":"KG"}
]
```

**Erros** — Aplica os erros comuns das rotas protegidas.

## Estoque

**Payload JSON** de `POST /estoque/entrada` e `POST /estoque/saida`

| Campo | Tipo | Obrigatório | Validação |
| --- | --- | --- | --- |
| `ingredienteId` | inteiro | Sim | ID positivo de um ingrediente existente. |
| `compartimentoId` | inteiro | Sim | ID positivo de um compartimento existente. |
| `quantidade` | decimal | Sim | Quantidade positiva a partir de `0.001`, com até nove dígitos inteiros e três casas decimais. |

```json
{"ingredienteId":1,"compartimentoId":1,"quantidade":5.125}
```

### Modelo de movimentação

As duas rotas de estoque retornam este objeto. No histórico, cada item de `content` usa o mesmo modelo.

| Campo | Tipo | Descrição |
| --- | --- | --- |
| `id` | inteiro | ID da movimentação. |
| `operacao` | string | `INBOUND` na entrada ou `OUTBOUND` na saída. |
| `ingredienteId` | inteiro | ID do ingrediente movimentado. |
| `ingrediente` | string | Nome do ingrediente. |
| `tipo` | string | Tipo de ingrediente. Consulte os [valores aceitos](#valores-aceitos). |
| `quantidade` | decimal | Quantidade movimentada na unidade do tipo de ingrediente. O valor é positivo em ambas as operações. |
| `compartimentoId` | inteiro | ID do compartimento. |
| `compartimento` | string | Código do compartimento, de `C1` a `C5`. |
| `responsavel` | string | Login do usuário que registrou a operação. |
| `dataHora` | string | Data e hora do registro. |

### `POST /estoque/entrada`

Adiciona a quantidade ao saldo do ingrediente no compartimento.

**Resposta · `201 Created`**

```json
{
  "id": 2,
  "operacao": "INBOUND",
  "ingredienteId": 1,
  "ingrediente": "Arroz",
  "tipo": "DRY",
  "quantidade": 5.125,
  "compartimentoId": 1,
  "compartimento": "C1",
  "responsavel": "admin",
  "dataHora": "2026-11-18T14:01:00"
}
```

**Erros** — `400 INVALID_REQUEST`, `400 INSUFFICIENT_CAPACITY`, `400 INCOMPATIBLE_INGREDIENT_TYPE`, `400 COMPARTMENT_TEMPORARILY_BLOCKED`, `404 INGREDIENT_NOT_FOUND` ou `404 COMPARTMENT_NOT_FOUND`.

### `POST /estoque/saida`

Reduz o saldo do ingrediente no compartimento pela quantidade solicitada.

**Resposta · `201 Created`**

```json
{
  "id": 3,
  "operacao": "OUTBOUND",
  "ingredienteId": 1,
  "ingrediente": "Arroz",
  "tipo": "DRY",
  "quantidade": 5.125,
  "compartimentoId": 1,
  "compartimento": "C1",
  "responsavel": "admin",
  "dataHora": "2026-11-18T14:02:00"
}
```

**Erros** — `400 INVALID_REQUEST`, `400 INSUFFICIENT_STOCK`, `400 COMPARTMENT_TEMPORARILY_BLOCKED`, `404 INGREDIENT_NOT_FOUND` ou `404 COMPARTMENT_NOT_FOUND`.

## Compartimentos

As duas consultas retornam um array ordenado por código de compartimento. Se nenhum compartimento atender aos critérios, retornam `[]`.

| Campo | Tipo | Descrição |
| --- | --- | --- |
| `id` | inteiro | ID do compartimento. |
| `codigo` | string | Código do compartimento, de `C1` a `C5`. |
| `quantidadeDisponivel` | decimal | Capacidade livre na consulta de armazenamento ou saldo do tipo na consulta de retirada. |
| `unidadeMedida` | string | `KG` ou `L`, conforme o tipo informado. |

### `GET /compartimentos/disponiveis`

Lista os compartimentos compatíveis com o tipo informado e com espaço para armazenar toda a quantidade solicitada.

| Parâmetro de consulta | Tipo | Obrigatório | Valores e validação |
| --- | --- | --- | --- |
| `tipo` | string | Sim | `DRY`, `LIQUID` ou `REFRIGERATED`. |
| `quantidade` | decimal | Sim | Quantidade a armazenar. Mínimo de `0.001`, com até nove dígitos inteiros e três casas decimais. |

**Exemplo** `GET /compartimentos/disponiveis?tipo=DRY&quantidade=600`

**Resposta · `200 OK`**

```json
[
  {"id":2,"codigo":"C2","quantidadeDisponivel":600,"unidadeMedida":"KG"},
  {"id":3,"codigo":"C3","quantidadeDisponivel":600,"unidadeMedida":"KG"},
  {"id":4,"codigo":"C4","quantidadeDisponivel":600,"unidadeMedida":"KG"},
  {"id":5,"codigo":"C5","quantidadeDisponivel":600,"unidadeMedida":"KG"}
]
```

**Erros** — `400 INVALID_REQUEST` se faltar um parâmetro, o tipo for desconhecido ou a quantidade for inválida.

### `GET /compartimentos/disponiveis-para-venda`

Lista os compartimentos com saldo positivo do tipo solicitado. O saldo retornado soma todos os ingredientes daquele tipo no compartimento.

| Parâmetro de consulta | Tipo | Obrigatório | Valores aceitos |
| --- | --- | --- | --- |
| `tipo` | string | Sim | `DRY`, `LIQUID` ou `REFRIGERATED`. |

**Exemplo** `GET /compartimentos/disponiveis-para-venda?tipo=DRY`

**Resposta · `200 OK`**

```json
[{"id":1,"codigo":"C1","quantidadeDisponivel":12.125,"unidadeMedida":"KG"}]
```

**Erros** — `400 INVALID_REQUEST` se faltar o tipo ou se ele for desconhecido.

## Histórico

### `GET /historico`

Retorna as entradas e saídas registradas. Esta é a única rota paginada da API.

#### Paginação e ordenação

Todos os parâmetros de consulta são opcionais.

| Parâmetro | Tipo | Padrão | Valores aceitos e efeito |
| --- | --- | --- | --- |
| `sortBy` | string | `date` | `date` ordena pela data e hora. `compartment` ordena pelo código do compartimento. |
| `order` | string | `desc` | `asc` para ordem crescente ou `desc` para decrescente. Aplica-se ao campo de `sortBy`. |
| `page` | inteiro | `0` | Índice da página a partir de zero. |
| `size` | inteiro | `20` | Quantidade máxima de movimentações por página, de 1 a 100. |

Na ordenação por data, os empates usam código de compartimento e ID ascendentes. Na ordenação por compartimento, os empates usam data descendente e ID ascendente.

Comece com `GET /historico?sortBy=date&order=desc&page=0&size=1`. Para avançar, mantenha os demais parâmetros e incremente `page` enquanto `page.number + 1 < page.totalPages`.

**Resposta · `200 OK`**

| Campo | Tipo | Descrição |
| --- | --- | --- |
| `content` | array | [Movimentações](#modelo-de-movimentação) da página. Retorna `[]` se o histórico estiver vazio ou se a página estiver além da última. |
| `page` | objeto | Metadados de paginação. |
| `page.size` | inteiro | Tamanho solicitado para a página. |
| `page.number` | inteiro | Índice da página retornada, a partir de zero. |
| `page.totalElements` | inteiro | Total de movimentações no histórico. |
| `page.totalPages` | inteiro | Total de páginas para o tamanho solicitado. Retorna `0` quando o histórico está vazio. |

```json
{
  "content": [
    {
      "id": 3,
      "operacao": "OUTBOUND",
      "ingredienteId": 1,
      "ingrediente": "Arroz",
      "tipo": "DRY",
      "quantidade": 5.125,
      "compartimentoId": 1,
      "compartimento": "C1",
      "responsavel": "admin",
      "dataHora": "2026-11-18T14:02:00"
    }
  ],
  "page": {"size":1,"number":0,"totalElements":3,"totalPages":3}
}
```

**Erros** — `400 INVALID_REQUEST` para parâmetros fora dos valores aceitos ou se `page × size` exceder o limite de um inteiro de 32 bits.
