# Referência da API

A URL local padrão é `http://localhost:8080`. Os corpos de requisição e resposta usam JSON. Nas rotas protegidas, envie `Authorization: Bearer <accessToken>`. Os exemplos partem de um banco novo e usam IDs ilustrativos.

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

## Autenticação

### `POST /auth/login`

Emite um JWT. A rota é pública e recebe apenas o corpo, com `login` e `senha` obrigatórios.

```json
{"login":"admin","senha":"admin123"}
```

**200 OK**

```json
{
  "accessToken": "<JWT emitido>",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "usuario": {"id":1,"nome":"Administrador","login":"admin"}
}
```

As credenciais acima são públicas e exclusivas para avaliação local. `expiresIn` vem em segundos.

**Erros** — `400 INVALID_REQUEST` para corpo inválido. `401 INVALID_CREDENTIALS` para login ou senha incorretos.

## Ingredientes

### `POST /ingredientes`

Rota protegida. Cadastra um ingrediente. Recebe um corpo com `nome` de até 150 caracteres e `tipo` igual a `DRY`, `LIQUID` ou `REFRIGERATED`. Para incluir saldo inicial, envie juntos `quantidade` e `compartimentoId`. O ID deve ser positivo, e a quantidade deve ser pelo menos `0.001`, com até nove dígitos inteiros e três casas decimais.

```json
{"nome":"Arroz","tipo":"DRY","quantidade":12.125,"compartimentoId":1}
```

**201 Created**

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

No cadastro simples, `quantidade`, `compartimento` e `movimentacao` são nulos.

**Erros** — `400 INVALID_REQUEST` para dados inválidos. A entrada inicial pode retornar `400 INSUFFICIENT_CAPACITY`, `400 INCOMPATIBLE_INGREDIENT_TYPE`, `400 COMPARTMENT_TEMPORARILY_BLOCKED` ou `404 COMPARTMENT_NOT_FOUND`.

### `GET /ingredientes`

Rota protegida. Lista os ingredientes em ordem de ID. A requisição usa apenas a rota.

**200 OK**

```json
[{"id":1,"nome":"Arroz","tipo":"DRY"}]
```

**Erros** — Aplica os erros comuns das rotas protegidas.

### `GET /ingredientes/{id}`

Rota protegida. Consulta um ingrediente pelo parâmetro de caminho `id`, inteiro de 64 bits.

**200 OK**

```json
{"id":1,"nome":"Arroz","tipo":"DRY"}
```

**Erros** — `400 INVALID_REQUEST` se o ID não puder ser convertido. `404 INGREDIENT_NOT_FOUND` se o ingrediente não existir.

### `GET /ingredientes/volume`

Rota protegida. Soma o saldo atual por tipo e inclui tipos com saldo zero. A requisição usa apenas a rota.

**200 OK**

```json
[
  {"tipo":"DRY","quantidadeTotal":12.125,"unidadeMedida":"KG"},
  {"tipo":"LIQUID","quantidadeTotal":0,"unidadeMedida":"L"},
  {"tipo":"REFRIGERATED","quantidadeTotal":0,"unidadeMedida":"KG"}
]
```

**Erros** — Aplica os erros comuns das rotas protegidas.

## Estoque

As duas rotas são protegidas e aceitam o mesmo corpo. `ingredienteId` e `compartimentoId` são IDs positivos obrigatórios. `quantidade` é obrigatória, com mínimo de `0.001` e até nove dígitos inteiros e três casas decimais.

```json
{"ingredienteId":1,"compartimentoId":1,"quantidade":5.125}
```

### `POST /estoque/entrada`

Adiciona a quantidade ao saldo do ingrediente no compartimento.

**201 Created**

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

**201 Created**

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

### `GET /compartimentos/disponiveis`

Rota protegida. Lista por código os compartimentos compatíveis com a quantidade solicitada. `quantidadeDisponivel` representa a capacidade livre. Os parâmetros de consulta `tipo` e `quantidade` são obrigatórios. A quantidade tem mínimo de `0.001` e até nove dígitos inteiros e três casas decimais.

**Exemplo** — `GET /compartimentos/disponiveis?tipo=DRY&quantidade=600`

**200 OK**

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

Rota protegida. Lista por código os compartimentos com saldo positivo do tipo solicitado. `tipo` é um parâmetro de consulta obrigatório. `quantidadeDisponivel` representa a soma do saldo daquele tipo no compartimento.

**Exemplo** — `GET /compartimentos/disponiveis-para-venda?tipo=DRY`

**200 OK**

```json
[{"id":1,"codigo":"C1","quantidadeDisponivel":12.125,"unidadeMedida":"KG"}]
```

**Erros** — `400 INVALID_REQUEST` se faltar o tipo ou se ele for desconhecido.

## Histórico

### `GET /historico`

Rota protegida. Retorna as movimentações paginadas. Os parâmetros de consulta são opcionais.

| Parâmetro | Padrão | Valores |
| --- | --- | --- |
| `sortBy` | `date` | `date` ou `compartment`. |
| `order` | `desc` | `asc` ou `desc`. |
| `page` | `0` | Inteiro a partir de zero. |
| `size` | `20` | Inteiro de 1 a 100. |

Na ordenação por data, os empates usam código de compartimento e ID ascendentes. Na ordenação por compartimento, usam data descendente e ID ascendente.

**Exemplo** — `GET /historico?size=1`

**200 OK**

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
