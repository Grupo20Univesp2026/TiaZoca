# Verificação da implementação

Verificações executadas em 12 de outubro de 2026, com Java 21 e Spring Boot 3.5.16.

## Backend e banco

O comando Maven `verify` passou com **5 testes, 0 falhas e 0 erros**, usando PostgreSQL 17.11 real em um banco temporário local. O Flyway criou as tabelas e o catálogo com os sete equipamentos.

A suíte verifica autenticação e CSRF, cadastro e validação, permissões da secretaria, liberação por sala, datas inválidas, conflitos de equipamento, preservação da sala original da reserva, desativação de conta e histórico concluído. Duas reservas simultâneas para o mesmo item resultam em apenas uma confirmação.

O banco e os processos temporários do PostgreSQL foram encerrados após a verificação. Nenhum serviço Windows foi instalado.

## Telas e execução

No navegador, com contas fictícias em banco H2 separado, foram conferidos:

- Cadastro e login da professora.
- Formulário bloqueado enquanto a secretaria não atribui uma sala.
- Atribuição da sala PPA pela secretaria.
- Reserva de Notebook Cinza com retirada e devolução e registro na tabela.
- Alteração para M2C sem alterar a sala gravada na reserva anterior.
- Diálogo explícito de confirmação, remoção e consulta da conta removida.
- Layout em celular de 390 pixels, sem transbordamento horizontal da página.

O JavaScript passou em `node --check`, o endpoint de saúde respondeu `UP` e a página inicial respondeu HTTP 200. Não foram observados erros de JavaScript na revisão do navegador.

## GitHub

O código foi enviado à branch `main` de [Grupo20Univesp2026/TrabalhoPi](https://github.com/Grupo20Univesp2026/TrabalhoPi). O planejamento e o relatório que já existiam no repositório foram preservados.

A [execução 38301248046 do GitHub Actions](https://github.com/Grupo20Univesp2026/TrabalhoPi/actions/runs/38301248046) concluiu com sucesso a verificação do projeto com Java 21.

## Render

O serviço informado inicialmente retornou `AccessDenied` na conta/workspace atual. Foram configurados novos recursos gratuitos para este projeto, ambos na região Ohio:

| Recurso | Resultado observado |
| --- | --- |
| [Web Service `srv-db6junqj9qps73cn7drg`](https://dashboard.render.com/web/srv-db6junqj9qps73cn7drg) | Docker, plano Free, branch `main` do repositório público |
| [PostgreSQL `dpg-db6jpibrjlhs73f3uas0-a`](https://dashboard.render.com/d/dpg-db6jpibrjlhs73f3uas0-a) | PostgreSQL 17, plano Free, acesso externo bloqueado |
| Sistema publicado | [https://tia-zoca-severina.onrender.com](https://tia-zoca-severina.onrender.com) |
| Validade do banco | Expira em **11 de novembro de 2026**, conforme o painel |
| Auto-Deploy | Desativado; atualizações exigem **Manual Deploy → Deploy latest commit** |

O [deploy `dep-db6k3bvf3r2c73cm3vvg`](https://dashboard.render.com/web/srv-db6junqj9qps73cn7drg/deploys/dep-db6k3bvf3r2c73cm3vvg), do commit `ad6441e`, concluiu com sucesso e chegou ao estado **Live** em 12 de outubro de 2026, às **17h25min04s, horário de Brasília** (`America/Sao_Paulo`). O **build Docker concluiu com sucesso** e executou **5 testes, 0 falhas**. A imagem foi construída pelo Render; não havia Docker instalado no computador local.

As variáveis `SPRING_PROFILES_ACTIVE=prod`, `PORT=8080`, `SECRETARY_NAME` e os cinco componentes `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME` e `DB_PASSWORD` foram configurados no serviço. O usuário informou `SECRETARY_EMAIL` e `SECRETARY_PASSWORD` diretamente no painel do Render. Nenhum valor secreto foi colocado no código ou nesta documentação.

Após a publicação, os logs do serviço confirmaram a conexão com **PostgreSQL 17.11** e a validação do esquema **Flyway V1**. As seguintes requisições foram verificadas no endereço público:

| Requisição | Resultado |
| --- | --- |
| `GET /` | HTTP **200** |
| `GET /actuator/health` | Saúde **`UP`** |
| `GET /api/teachers`, sem login | HTTP **401**, acesso negado ao usuário anônimo |

Esses resultados confirmam a publicação e a saúde do serviço público. Os cenários completos de cadastro, atribuição de sala e reserva foram exercitados nos testes e na revisão local do navegador descritos acima.

O serviço usa a origem **Public Git Repository**, porque a credencial Git Provider disponível estava conectada a outra conta GitHub. Para atualizar, envie o código com `git push`, confira o Actions e execute o deploy manual no Render. A conexão da conta GitHub correta e os deploys automáticos podem ser configurados posteriormente, se desejado.

O `render.yaml` permanece como alternativa para uma instalação nova. Não foi aplicado para criar estes recursos; aplicá-lo sem adaptação pode duplicar serviço e banco.
