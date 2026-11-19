# Testes automatizados

A suíte usa JUnit Jupiter e roda pelo Maven Surefire. As integrações carregam o Spring Boot, usam MockMvc para as chamadas HTTP e verificam o PostgreSQL com JdbcTemplate e AssertJ. O teste de concorrência usa Awaitility para observar a espera pelo bloqueio no banco.

## Como executar

Inicie o PostgreSQL e configure o `.env` conforme o [README](../README.md). A partir da raiz, execute:

```sh
cd backend
./mvnw clean test
```

O usuário de banco precisa de permissão para criar esquemas. Os testes HTTP e de regras usam um esquema por classe, criado com Flyway. A preparação restaura os dados de estoque a cada cenário. O esquema é removido ao final da classe. O teste de inicialização usa o esquema padrão da conexão configurada.

## Organização

As classes ficam em [backend/src/test/java/com/warehouse](../backend/src/test/java/com/warehouse/).

| Classe | Cobertura exercitada |
| --- | --- |
| `IngredientControllerTests` | Cadastro com e sem estoque inicial, validação e precisão de quantidades, listagem, consulta por ID, agregação por tipo e rollback quando a gravação do histórico falha. |
| `StockMovementTests` | Entradas, saídas totais e parciais, saldo insuficiente, capacidade, tipo incompatível, IDs inválidos, instante de esvaziamento, vínculo do operador e autenticação na movimentação. |
| `CompartmentTests` | Compatibilidade de tipo, troca de tipo no dia seguinte, reposição no mesmo dia, filtros de disponibilidade, espaço livre e estoque para retirada. |
| `StockHistoryTests` | Paginação, ordenação, desempates, página vazia, filtros inválidos e exigência de autenticação. |
| `AuthenticationTests` | Login inicial do administrador e bloqueio de uma requisição sem token. A expiração configurada e o uso do JWT emitido também são exercitados em `StockMovementTests`. |
| `StockConcurrencyTests` | Duas entradas concorrentes em C1 disputando capacidade. |
| `WarehouseApiApplicationTests` | Carregamento do contexto da aplicação com o banco configurado. |
| `PasswordEncoderTest` | Testes unitários do BCrypt com senhas corretas, incorretas e hashes para senhas distintas. |

## Cenário de concorrência

O teste segura a linha de C1 em uma transação, inicia duas requisições de 400 kg e usa `pg_stat_activity` para confirmar que ambas aguardam o bloqueio. Depois da liberação, uma é aceita e a outra recebe `400 INSUFFICIENT_CAPACITY`. O teste confirma que só a entrada aceita alterou o ingrediente, o saldo e o histórico.

## Limites da suíte

A suíte atual exercita autenticação ausente e login válido. Casos específicos de credenciais incorretas no endpoint, JWT adulterado ou expirado e usuário removido após emissão do token ainda não têm teste dedicado. O cenário de concorrência cobre duas entradas no mesmo compartimento. Saídas simultâneas e entradas em compartimentos diferentes ficam fora desse cenário.
