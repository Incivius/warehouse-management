# Requisitos e regras de negócio

## Introdução

O desafio propõe uma API REST para gerenciar o estoque de ingredientes de uma fábrica de alimentos. A API deve cadastrar, movimentar e consultar ingredientes, respeitar as restrições de armazenamento e registrar o histórico das operações. [PDF original do desafio](reference/Desafio_Tecnico_-_Java_Pleno_%28Vega%29.pdf)

## Requisitos funcionais do desafio

| Requisito original | Implementação |
| --- | --- |
| Cadastrar um ingrediente em um compartimento com tipo e quantidade válidos. Consultar os ingredientes cadastrados. | `POST /ingredientes` aceita estoque inicial quando `quantidade` e `compartimentoId` são fornecidos juntos. A API implementa tanto `GET /ingredientes` quanto `GET /ingredientes/{id}`. |
| Consultar o total armazenado por tipo. | `GET /ingredientes/volume` agrega os saldos dos secos, líquidos e refrigerados e inclui tipos com saldo zero. |
| Consultar compartimentos disponíveis para armazenamento pela quantidade e pelo tipo. | `GET /compartimentos/disponiveis` calcula a capacidade livre e aplica as regras de compatibilidade. |
| Consultar compartimentos com volume positivo do tipo solicitado para retirada. | `GET /compartimentos/disponiveis-para-venda` agrega o saldo pelo tipo e pelo compartimento. |
| Registrar entradas e saídas com data e hora, operação, quantidade, tipo de ingrediente e responsável. | As operações bem-sucedidas gravam a movimentação junto com a atualização do saldo. O histórico expõe todos os dados solicitados. |
| Consultar o histórico com ordenação por data ou compartimento e direção configurável. | `GET /historico` aceita `date` ou `compartment` em `sortBy`, e `asc` ou `desc` em `order`. |

## Regras de negócio do desafio

- O armazém tem cinco compartimentos, de C1 a C5. Cada um pode armazenar vários ingredientes de um único tipo por vez.
- Uma entrada exige espaço suficiente. O limite se aplica à soma dos saldos no compartimento.
- Se um compartimento armazenou secos hoje, só pode receber outro tipo a partir do dia seguinte.
- Cadastros que violem a capacidade ou a compatibilidade de tipo devem retornar `400 Bad Request` com uma mensagem clara. A API retorna códigos específicos para capacidade insuficiente e tipo incompatível.
- Toda entrada ou saída concluída deve gerar histórico.

| Tipo | Capacidade por compartimento | Código na API |
| --- | ---: | --- |
| Secos | 600 kg | `DRY` |
| Líquidos | 500 L | `LIQUID` |
| Refrigerados | 400 kg | `REFRIGERATED` |

## Requisitos não funcionais e diferenciais

O PDF pede Java com Spring Boot, uso de anotações do framework, boas práticas REST e mensagens de erro claras com status HTTP adequados. O projeto usa Java 21 e Spring Boot, organiza as camadas com anotações do Spring e padroniza as respostas de erro.

Para a entrega, o PDF pede que o código completo seja publicado no GitHub, com instruções de execução no README e acesso para a avaliação por convite.

O PDF lista testes automatizados, controle de concorrência, documentação de arquitetura, eficiência do cálculo de espaço e frontend como diferenciais. O projeto inclui testes unitários e de integração, bloqueio pessimista por compartimento, documentação técnica e agregações de saldo feitas no banco. A entrega atual é uma API REST sem frontend.

## Diferenças entre o PDF e a API

| Aspecto | PDF | Implementação |
| --- | --- | --- |
| Cadastro | O exemplo inclui `quantidade`, `compartimentoId` e `responsavel`. | O cadastro também funciona sem estoque inicial. Com estoque, quantidade e compartimento são informados juntos. O responsável é obtido do usuário autenticado e aparece na resposta. |
| Tipos e totais | Os exemplos usam nomes em português, como `SECOS` e `SECO`, e campos `type` e `totalQuantity` para o volume. | Os tipos são `DRY`, `LIQUID` e `REFRIGERATED`. O volume usa `tipo`, `quantidadeTotal` e `unidadeMedida`. |
| Entradas, saídas e histórico | Exige o registro das duas operações, descritas como `ENTRADA` e `SAÍDA`. A lista de endpoints não define rotas próprias para movimentação nem paginação do histórico. | A API expõe `POST /estoque/entrada` e `POST /estoque/saida`, retorna `INBOUND` e `OUTBOUND` e oferece histórico paginado. |
| Troca de tipo | A regra de passar para o dia seguinte é descrita para compartimentos que armazenaram secos. | A mesma restrição vale para os três tipos. Ao zerar o saldo, o compartimento guarda o último tipo e o momento do esvaziamento. A troca é permitida no dia seguinte, pelo fuso da JVM. A reposição do mesmo tipo é permitida no mesmo dia. |
| Persistência | Cita H2, MySQL ou banco em memória como opções. | O ambiente documentado usa PostgreSQL 17 com Docker Compose e Flyway. |

## Outras decisões de implementação

- **Autenticação** — O desafio exige identificar o responsável pela movimentação. A API usa `POST /auth/login`, senhas verificadas com BCrypt e JWT para autenticar as demais rotas.
- **Integridade e concorrência** — O banco usa chaves estrangeiras, unicidade do saldo por ingrediente e compartimento e restrições de valores. Uma transação reúne as gravações de saldo, estado e histórico. O bloqueio pessimista serializa movimentações concorrentes no mesmo compartimento.
- **Quantidades** — A aplicação aceita a partir de `0.001`, com até nove dígitos inteiros e três casas decimais. Cada entrada respeita a capacidade livre. Cada saída respeita o saldo do ingrediente no compartimento.
- **Estado `BLOCKED`** — Impede movimentações e exclui o compartimento da consulta para armazenamento. A consulta para retirada filtra pelo saldo positivo do tipo e pode listar um compartimento bloqueado que ainda tenha saldo. A restrição temporal é calculada separadamente, a partir da data de esvaziamento e do último tipo.

Consulte os [contratos HTTP](api.md) e a [arquitetura](architecture/architecture.md) para os detalhes de implementação.
