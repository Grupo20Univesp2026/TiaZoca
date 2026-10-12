# Tia Zoca — Agendamento de equipamentos multimídia

Sistema para a Escola Severina Barbosa “Tia Zoca”, desenvolvido para o Projeto Integrador com **HTML, CSS e JavaScript no frontend e Java no backend**. As professoras criam suas contas, a secretaria define a sala de cada professora e, depois dessa liberação, os equipamentos podem ser reservados.

O sistema está publicado em [tia-zoca-severina.onrender.com](https://tia-zoca-severina.onrender.com), com backend Java e banco PostgreSQL no Render. 

## Funcionalidades

- Cadastro de professora com nome, e-mail e senha; login por e-mail.
- Conta de secretaria com acesso exclusivo à gestão de professoras.
- Liberação de professora e definição de sala pela secretaria.
- Alteração de sala durante o ano.
- Remoção de professora com confirmação e preservação do histórico.
- Agendamento com equipamento, data de retirada e data de devolução.
- Tabela compartilhada com professora, sala, equipamento e período reservado.
- Bloqueio de duas reservas do mesmo equipamento em datas que se sobrepõem.
- Senhas armazenadas com hash BCrypt; sessões e proteção contra CSRF.

**Salas:** PPA, PPB, PPC, M1A, M1B, M1C, M2A, M2B e M2C.

**Equipamentos:** Tv 32, Tv 40, Tv 50, Retroprojetor, Notebook Cinza, Notebook Preto e Caixa de Som. 

As duas datas fazem parte do período ocupado. Exemplo: uma reserva entre 12 e 14 de outubro impede outra reserva do mesmo equipamento no dia 14; ele pode voltar a ser reservado a partir do dia 15. Retirada e devolução no mesmo dia são permitidas.

## Tecnologias

| Parte | Tecnologia |
| --- | --- |
| Telas | HTML, CSS e JavaScript |
| Backend | Java 21 e Spring Boot 3.5.16 |
| Autenticação | Spring Security, sessão e BCrypt |
| Persistência | Spring JDBC e migrações Flyway |
| Banco local | H2 em arquivo |
| Banco de produção | PostgreSQL |
| Build | Maven Wrapper |
| Hospedagem | Docker no Render, com PostgreSQL 17 |
| Verificação automática | GitHub Actions |


## Como usar

1. Na página inicial, a professora escolhe **Criar conta** e informa nome, e-mail e senha.
2. A professora entra com suas credenciais. Enquanto não tiver uma sala atribuída, aguarda a liberação da secretaria.
3. A secretaria entra, abre sua área exclusiva, seleciona a professora e uma sala e confirma o cadastro.
4. A professora abre a área de agendamento. O sistema apresenta a sala atribuída e permite selecionar equipamento, retirada e devolução.
5. Ao confirmar, a reserva aparece na tabela. Um período indisponível apresenta uma mensagem e não gera outra reserva.
6. A secretaria pode selecionar a professora para alterar a sala ou removê-la. A remoção pede confirmação, bloqueia o acesso dessa conta e mantém os registros históricos.

As senhas devem ter pelo menos 8 caracteres e no máximo 72 bytes em UTF-8. Professora e secretaria usam o mesmo mecanismo de login, mas o backend verifica o perfil em cada ação. Uma professora ainda sem sala pode consultar a agenda, porém não consegue reservar. Ao remover uma professora, a secretaria também cancela suas reservas em andamento ou futuras; as anteriores permanecem no histórico.

## Hospedagem no Render

O projeto usa um **Web Service com Docker**

| Recurso | Configuração e acesso |
| --- | --- |
| Web Service | Docker, plano Free, região Ohio — [painel do serviço](https://dashboard.render.com/web/srv-db6junqj9qps73cn7drg) |
| Endereço público | [https://tia-zoca-severina.onrender.com](https://tia-zoca-severina.onrender.com) |
| Banco | PostgreSQL 17.11, plano Free, região Ohio — [painel do banco](https://dashboard.render.com/d/dpg-db6jpibrjlhs73f3uas0-a) |
| Expiração do banco | **11 de novembro de 2026** |
| Origem do código | Public Git Repository, branch `main` de `Grupo20Univesp2026/TrabalhoPi` |
| Atualizações | Auto-Deploy desativado; deploy manual após enviar o código |

