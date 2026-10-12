# Roteiro para apresentar o Projeto Integrador

## Objetivo

Organizar a reserva dos equipamentos multimídia da Escola Severina Barbosa “Tia Zoca”. O sistema identifica a professora e a sala, registra o período e impede que o mesmo item seja prometido a duas pessoas nas mesmas datas.

## Demonstração sugerida

1. **Mostrar o código:** abrir uma tela HTML, um controller Java e a migração SQL. Explicar que o GitHub contém os fontes e que o Render executa o backend Java.
2. **Criar conta:** cadastrar uma professora fictícia com nome, e-mail e senha.
3. **Mostrar o controle da secretaria:** entrar como professora e apresentar a mensagem de espera pela sala.
4. **Liberar a professora:** sair, entrar como secretaria, selecionar a conta e atribuir a sala PPA.
5. **Agendar um equipamento:** entrar novamente como professora, mostrar a sala e reservar Tv 32 para amanhã e o dia seguinte.
6. **Mostrar a tabela:** conferir professora, sala, equipamento, retirada e devolução.
7. **Mostrar a regra de conflito:** tentar reservar Tv 32 em uma data dentro do período anterior. O sistema deve recusar. Uma reserva de Tv 40 no mesmo período deve ser permitida.
8. **Alterar sala:** entrar como secretaria e mover a professora para M1A. A conta passa a apresentar M1A; uma reserva anterior conserva a sala registrada quando foi criada.
9. **Mostrar a confirmação de remoção:** selecionar a professora, abrir a confirmação e cancelar. Se precisar demonstrar a remoção completa, use uma segunda conta fictícia.
10. **Mostrar persistência e testes:** apresentar o resultado de `mvnw verify` e explicar que os dados são armazenados em banco. Localmente, H2; no Render, PostgreSQL.

Abra o site no Render alguns minutos antes da apresentação: o serviço gratuito pode precisar iniciar após um período sem acessos. Confira também se o PostgreSQL gratuito ainda está dentro dos 30 dias de validade. [Limites do Render](https://render.com/docs/free).

## Explicação técnica curta

“O frontend usa HTML, CSS e JavaScript. Ele envia as ações ao backend Java com Spring Boot. O backend valida o usuário, o perfil e as datas antes de gravar a reserva no banco. A secretaria controla quais professoras podem agendar. As senhas são armazenadas como hash, e a verificação de disponibilidade acontece no servidor.”

## Evidências para entregar

- URL do repositório GitHub com o código-fonte.
- URL pública do sistema no Render, depois da publicação.
- Capturas da criação de conta, gestão da secretaria, reserva confirmada e rejeição de conflito.
- Modelo de dados com usuários, equipamentos e reservas.
- Resultado dos testes do backend.

Confira se o professor exige formato específico de relatório, diagrama ou número de páginas. Este roteiro descreve a implementação; não substitui as regras da disciplina.
