# Roadmap de integração do ACC dev no ACC-NG

9 de outubro de 2026. Integrar as melhorias do ACC até `908a5a4` no nosso motor, mantendo perfis, diagnósticos, notificações e instalação pela AccA-NG. A [revisão](acc-upstream-review.md) identifica os conflitos; o [catálogo](acc-upstream-commits.md) cobre os 202 commits e as reversões.

**Estado:** I1/I2 integrados; **I3 implementado no candidato v1.0.6-ng (202610106)**, mantendo API NG 1 e schema 202310160. I3 cobre descoberta/cache, blacklist/validação e alimentação externa comum, com readback de corrente/tensão na app. Fonte do motor em [`1e8291b`](https://github.com/ricardojrgpimentel/ACC-NG/tree/1e8291bcc69d59de677674728075b0c9063b561a), sobre `2d969cb`; release pendente. I3 está instalado no Samsung: upgrade/configuração preservada e deteção de escritas de corrente recusadas passaram; tensão ficou pendente durante cooldown. A configuração original foi reposta. Aplicação/restauração efetiva de limites e matriz completa continuam pendentes. USB físico e arranque Magisk antes do PIN passaram em I2, não foram repetidos em I3. Ver o [registo de implementação](acc-upstream-implementation.md#i3--descoberta-alimentação-e-aplicação-de-limites).

## Estado das tarefas

Esta tabela acompanha o trabalho; as especificações R0–R7 abaixo continuam a definir os critérios completos. “Implementado” indica código e fixtures, não aprovação para release. Cada incremento deve atualizar esta tabela, o catálogo e o registo de validação.

| Tarefa | Estado atual | Implementado / restante |
| --- | --- | --- |
| R0.1 | Implementado I2 em fixtures | Outputs de versão, estado, informação, defaults/perfis, switches e testes executados pelo motor e lidos pela app; checks de CI contra o bundle. |
| R0.2 | Implementado I2 | ngConfigSchema/ngCapabilities explícitos; compatibilidade com metadata API 1 antiga; schemas/API desconhecidos não selecionam um writer presumido. |
| R0.3 | Implementado I2 em fixtures | Perfis JSON antigos/atuais, escrita/reload raw, limites, r, switch manual, calibração e preferências NG; upgrade real continua R7. |
| R0.4 | Implementado I2 em fixtures | Harness host/Android de sensores mA/µA e mV/µV, polaridade, zero, dados ausentes/inválidos/recusados, mais controlos de I1. Descoberta OEM e sysfs real continuam R2/R7. |
| R1.1 | Implementado I1; validação física pendente | Resets só de controlos alterados pelo NG, com snapshot e retry em falha. |
| R1.2 | Implementado I3 em fixtures | Cache por boot/tipo, deduplicação de aliases, defaults originais e suporte explícito; aplicação lê o pedido mais recente sem regravar configuração antiga. Validação OEM real pendente. |
| R1.3 | Implementado I1; validação física pendente | Escrita direta, sem chown; fallback u+w temporário com restauração e erro propagado. Falta ensaio de sysfs/política OEM. |
| R1.4 | Implementado I3; validação física parcial | Readback distingue gravado/pendente, aplicado nos controlos, sem suporte e falha. Recusa real de corrente reportada como falha no Samsung; aplicação efetiva continua pendente. |
| R2.1 | Implementado I3 em fixtures | Blacklist final de acc -p, validação de candidatos/parsed.log e referências térmicas, sem chmod na descoberta nem fallback arbitrário. Escolha manual preservada. |
| R2.2–R2.3 | Pendente | Novos switches/regras OEM e descoberta de sensores alternativos ainda por integrar. |
| R2.4 | Implementado I3 em fixtures | Um helper comum no motor, eventos e app; Battery/BMS/OTG excluídos, fontes OEM aceites e desconhecido conservado, incluindo leitura inválida junto de fonte offline. |
| R3.1–R3.6 | Pendente | Testes de switches, estados, recuperação e proteções mantêm a implementação atual. |
| R4.1–R4.4 | Pendente | Import, escrita/migração e agendamento por integrar. |
| R5.1–R5.5 | Pendente | Arranque, root, instalação, downloader e diagnósticos por integrar. |
| R6.1–R6.5 | Opcional pendente | Não incluído em I1. |
| R7 | Parcial I1/I2/I3 | Bundle determinístico, fixtures host/Android, testes e build/lint da app. I3: upgrade/config preservada, recusa de corrente e recuperação de instalação falhada passaram. I2: pausa/retoma, USB e arranque antes do desbloqueio passaram. Aplicação/restauração efetiva de limites, matriz completa e release pendentes. |


## Estratégia de integração

Portar o comportamento final por grupos de ficheiros, referenciando os commits de origem. Vários commits misturam correções úteis com mudanças de API, defaults, metadata ou experiências entretanto revertidas; não usar cherry-pick indiscriminado. Cada grupo deve poder ser revisto e revertido isoladamente.

O contrato inicial continua `ngApiVersion=1`: `acca -i` conserva campos e unidades, `capacity` mantém seis elementos, calibração manual e `resume_temp=...r` continuam suportados. O número da release NG não deve ser interpretado como a versão da implementação upstream. Manter a base de origem identificada e registar separadamente o commit upstream revisto e os grupos efetivamente portados.

| Fase | Prioridade | Dependências | Entrega |
| --- | --- | --- | --- |
| R0 Contrato e fixtures | P0 | Revisão concluída | Compatibilidade verificável entre app e motor |
| R1 Corrente tensão e permissões | P1 | R0 | Resets condicionais e aplicação consistente |
| R2 Sensores e compatibilidade OEM | P1 | R0, partes de R1 | Descoberta validada e base de switches revista |
| R3 Estados testes e proteções | P1 | R1 e R2 | Testes de switches, idle e recuperação de erro 7 |
| R4 Configuração e agendamento | P1 | R0; R3 para nova semântica térmica | Import e escrita robustos sem perder perfis |
| R5 Arranque instalação e diagnóstico | P1 | R0; R1 a R4 antes de candidato completo | Recuperação e distribuição coerentes com NG |
| R6 Funcionalidades opcionais | P2 | Núcleo R1 a R5 validado | Idle por app, Encore e opções avançadas opt-in |
| R7 Validação e release | P1 | R0 a R5; R6 só se incluída | Candidato conjunto do motor e app com evidência |

I2 completa a base R0 em fixtures, sem substituir a validação física R7. I1 cobre resets/permissões de R1 e a exclusão store_mode de R2; o restante destes grupos continua pendente. R4 pode avançar após R0 para import/quoting sem antecipar alterações térmicas de R3. R6 não bloqueia a release das correções principais.

## R0 Contrato e fixtures

| Tarefa | Alteração | Critério de conclusão |
| --- | --- | --- |
| R0.1 | Criar fixtures do motor atual: `-v`, `-D`, `-i`, `-sp`, defaults, switches e saída de testes | O handler atual lê informação e configuração sem perda de campos, unidades ou flags. Estado distingue 0/8 ativo, 9 parado e falhas. |
| R0.2 | Explicitar capacidades do motor NG, sem selecionar funcionalidades pela data da release | Metadados ausentes degradam de forma compatível; não anunciam funcionalidades não implementadas. `ngApiVersion=1` conserva o contrato atual. |
| R0.3 | Testar perfis antigos, atuais e configuração raw | Pausa 60/resume 50, shutdown, temperaturas, sufixo `r`, switch manual e preferências NG sobrevivem a aplicação, leitura e reinstalação. |
| R0.4 | Criar harness de sensores/controlo para corrente positiva/negativa, dados ausentes, mA/µA e mV/µV | Fixtures não precisam de root real e falham perante uma regressão comportamental, sem exigir nomes específicos de funções. |

**Motor:** `tests/test_engine.py`, `module.prop`, `install/acca.sh`, `print-config.sh`, `batt-info.sh`. **App:** `Acc.kt`, `AccNg.kt`, handler `v202107280`, `TemperatureConfig.kt`, `AccHealth.kt`, `ProfileActivation.kt` e testes correspondentes.

Não converter o output humano novo de dev na interface usada pelo dashboard. Acrescentar fixtures para aspas de configuração e para `battIdleMode` antes de mudar qualquer parser. Distinguir testes estáticos de contrato de execução real do shell Android.

## R1 Corrente tensão e permissões

Origens principais: `07a3a8d`, `eaa0c4d`, `486aa90`, `e89c463`, `39a2208`, `a74d83b`, `4b1bfd4`, `4999c63`. Portar o estado final útil com correções NG.

| Tarefa | Alteração | Critério de conclusão |
| --- | --- | --- |
| R1.1 | Repor defaults apenas para limites que o ACC efetivamente personalizou | `mcc=-`, `mcv=-` e `temp_level=0` sem personalização não escrevem sysfs nem aguardam um carregador. Um reset real repõe o valor capturado antes da alteração. |
| R1.2 | Isolar aplicação de corrente, tensão e `apply_on_boot/plug`; melhorar cache | Alterar mcc não repõe mcv; reiniciar não aprende um limite personalizado como default; ausência de controlo é distinta de aplicação bem sucedida. |
| R1.3 | Rever `write()` e `set_temp_level()` preservando acesso OEM | Proprietário/mode são preservados ou restaurados; falhas de escrita não criam marcadores de sucesso; nodes não ficam world-writable. |
| R1.4 | Alinhar aplicação imediata/diferida com readback da app | Configuração gravada, suporte inexistente e limite aplicado são distinguíveis. A app não anuncia sucesso físico só porque o valor foi gravado no config. |

**Motor:** `set-ch-curr.sh`, `set-ch-volt.sh`, `read-ch-curr-ctrl-files-p2.sh`, `batt-interface.sh`, `misc-functions.sh`, `accd.sh`, `acca.sh`. **App:** `ConfigUpdater.kt`, `ConfigVerifier.kt`, `ConfigPowerLimitsTest.kt` e handler.

Validar limites definidos e removidos, desconexão/reconexão, reinício do daemon, escrita recusada e falta de sensores. Em aparelho, verificar que a política térmica OEM ainda consegue atualizar os seus controlos. Não atribuir ao ACC um valor que era do sistema.

## R2 Sensores e compatibilidade OEM

Origens principais: `43ae881`, `939ed3f`, `f237b0b`, `69cfecb`, `d3752b1`, `705b762`, `9cdf64f`, `6b308d8`, `289017b`, `a2177e8`. Separar o switch `bypass_charger` da integração Encore do mesmo commit `09e38a4`.

| Tarefa | Alteração | Critério de conclusão |
| --- | --- | --- |
| R2.1 | Integrar exclusões: `store_mode`, controlos inválidos e blacklist final de descoberta | Nenhum candidato inválido é escrito; ficheiros ausentes, vazios ou com valores não numéricos não provocam fallback arbitrário. |
| R2.2 | Acrescentar switches novos e regras OEM por board | Não há duplicados; switch forçado pelo utilizador permanece escolhido; as regras só atuam no aparelho correspondente. Controlos experimentais têm validação separada. |
| R2.3 | Portar descoberta de bateria/status/temperatura e interfaces alternativas | Nó separado de status/capacidade e ausência de uevent funcionam; a escolha da corrente identifica o que está a medir e aplica a escala correta. |
| R2.4 | Unificar alimentação externa no motor, eventos e app | Battery/BMS/OTG não são carregadores; nomes novos e tipos válidos são aceites; desconhecido não equivale a unplug nem conclui calibração. |

**Motor:** `ctrl-files.sh`, `oem-custom.sh`, inicialização de `accd.sh`, `batt-interface.sh`, `ng-events.sh`. **App:** `ExternalPowerSupply.kt`, `AccTroubleshooter.kt`, `BatteryPowerState.kt` e testes.

Fixtures devem cobrir Samsung com `battery/online=1` e USB desligado, USB/AC/DC/wireless, charger com nome OEM, tipo Battery fora do nome esperado, OTG, enum `online` inválido, fontes simultâneas e leitura recusada. Não copiar a whitelist upstream como única fonte de verdade.

## R3 Estados testes e proteções

Origens principais: `b0b19f0`, `aa4f47d`, `6460a02`, `a4e1058`, `8af4502`, `02863a4`, `00e7d3f`, `b21384e`, `16df5bf`, `2e0bea2`, `bbd6ccc`, `2ed17de`, `872dd3d`, `dabed07`, `d0b79f4`.

| Tarefa | Alteração | Critério de conclusão |
| --- | --- | --- |
| R3.1 | Portar teste de switches e lista de resultados, corrigindo modo silencioso | Códigos 0/15/7/16 são preservados; aliases e parser de bypass funcionam; saída incompleta é erro, não ausência de suporte confirmada. |
| R3.2 | Rever teste ON/OFF, tentativas e sinais de teste ativo | Tempo total tem um limite compatível com a app; cancelamento repõe controlo e limpa flags; dois testes não escrevem simultaneamente. |
| R3.3 | Melhorar seleção automática e recuperação de erro 7 | Retomas inesperadas são contadas e reinicializações têm um orçamento; falha persistente gera diagnóstico e uma só instância do daemon. |
| R3.4 | Rever deteção de idle e polaridade automática | Amostras consistentes; 10 mA não é imposta sobre configurações existentes; corrente zero não desativa definitivamente o workaround; override manual continua disponível. |
| R3.5 | Portar distinção de pausa térmica e pausa por capacidade | Testes de transição definem quando se pode retomar; sufixo `r` e perfis existentes conservam o seu significado. |
| R3.6 | Manter cooldown interrompível e shutdown | Atingir pausa/max_temp durante `cooldown_charge` interrompe a espera dentro do orçamento do loop; shutdown e desconexão continuam avaliados. |

**App:** alinhar `isBatteryIdleSupported`, `testChargingSwitch`, `AccDaemonProcess`, `RootShell` e apresentação de diagnósticos. Não aumentar timeouts de todos os comandos só para acomodar uma espera do motor. **Motor:** não reintroduzir suspensão térmica nem redução fixa de corrente durante os testes, ambas removidas/revertidas upstream.

Os testes atuais de proteção incluem verificações da presença de funções. À medida que o loop mudar, substituí-las por cenários executados que verifiquem efeitos e prazos; uma refatoração de nome não deve ser confundida com perda de proteção.

## R4 Configuração e agendamento

Origens principais: `40c3a29`, `a1097c4`, `b29fd79`, `745a3ab`, `1017e14`, `ce3f1a8`, `85f6044`, `07a5d74`, `e248aef`, `a39bac1`, `d132eab`, `2d016bf`; scheduler final de `654fe5f`, `5855e06`, `d532126`.

| Tarefa | Alteração | Critério de conclusão |
| --- | --- | --- |
| R4.1 | Portar import parcial, CRLF e substituição de scripts por nome | Scripts não duplicam, quoting mantém significado e variáveis desconhecidas não são descartadas sem uma regra explícita. |
| R4.2 | Escrita/migração atómica com backup e recuperação | Config inválido ou falha a meio deixa o último config válido disponível; gravação concorrente não perde perfil nem `ngNotifications`/idioma. |
| R4.3 | Alinhar aspas, validação e defaults entre motor/app | Parser devolve switch/commands sem aspas artificiais; resume/pause têm unidades coerentes; normalizações são expostas no readback e nos perfis. |
| R4.4 | Rever `at()` e scripts nomeados, preservando DJS | Horários não duplicam execução; passagem de meia-noite e quoting são testados; comandos resolvem paths NG. Os horários DJS da app conservam funcionamento. |

Manter inicialmente o esquema atual. Uma eventual redução do array `capacity`, remoção de `capacity_sync`, migração de `dischargePolarity` ou mudança dos defaults 35/50/45 para 45/50/40 terá uma tarefa de migração própria, após R3. **Não converter nem apagar essas opções nesta fase por cópia do config de dev.**

Para suporte posterior a schemas diferentes, ler os metadados do contrato, converter para um modelo interno e escrever no schema respetivo. Uma versão desconhecida não deve usar índices presumidos. Histerese em mV e °C deve ser validada com inputs explícitos e perfis antigos.

## R5 Arranque instalação e diagnóstico

Origens principais: `af87dce`, `00d1f32`, `0fe4455`, `0e239f4`, `67f27ed`, `41b825e`, `03bff24`, `3ab7227`, `6333f1b`, `7000c59`; downloader/rollback devem usar o desenho NG.

| Tarefa | Alteração | Critério de conclusão |
| --- | --- | --- |
| R5.1 | Portar flags persistentes e arranque tolerante à inicialização | `-D` continua rápido; a app distingue a inicialização de falha; disable é respeitado também durante a espera; grace period de arranque é testado. |
| R5.2 | Alinhar wrappers, BusyBox e root managers | Magisk mantém instalação sem reboot; KSU/APatch têm ensaios próprios. Não usar deteção por glob de BusyBox como prova suficiente do gestor root. |
| R5.3 | Rever instala/desinstala/rollback preservando caminhos NG | Backups datados e recovery continuam funcionais; rollback encontra o snapshot NG, não presume o layout upstream; cleanup é limitado ao módulo e pacote correto. Falha observada em I3: cleanup apagou o próprio staging /data/local/tmp/acc[-_]*; recovery repôs I2. Corrigir e testar esse caminho. |
| R5.4 | Rever downloader e seleção de arquivos | Validação TLS, HTTP e conteúdo; branch/tag/archive compatíveis; falhas propagam o código real; identidade e URL continuam NG. |
| R5.5 | Portar logs, filtros e resultados de teste | Último teste e contexto de sensores entram no arquivo; export não bloqueia o loop e possui limites; recuperação mantém evidência útil. |

Preservar a correção NG de workers órfãos e espera de cinco segundos no lock. O caminho upstream antigo em `release-lock.sh` não substitui esta correção. Rever como flags e recuperação interagem com `ModernAccDaemon` e com a reparação do daemon empacotado.

A espera de 70 segundos de `acca` não entra como bloqueio antes de qualquer comando. Preparar um estado explícito de readiness, com retries apenas nas operações que realmente precisam dos sensores. Desativar uma inicialização em curso deve continuar possível.

## R6 Funcionalidades opcionais

| Tarefa | Condição | Critério de conclusão |
| --- | --- | --- |
| R6.1 `idle_apps` | Núcleo e capacidades concluídos | Pausa temporária tem motivo próprio; não altera o perfil, limites de proteção ou identidade de configuração; ausência de app ativa termina a condição de forma definida. |
| R6.2 Encore | Integração solicitada/útil e aparelho adequado | Ausência do ficheiro ou valor inválido não causa pausa; formatos antigo/novo são tratados; permanece opt-in e não substitui limites térmicos. |
| R6.3 Percentagens de corrente e cooldown | Motor, modelo e editor conseguem representar o tipo de limite | App não interpreta `50%` como 50 mA; suporte e aplicação são verificados; preferências sobrevivem a perfis. |
| R6.4 Máscara de capacidade e reset de estatísticas | Separação do nível físico/apresentado concluída | Controlos e shutdown usam a fonte correta; erros/restauração não deixam o serviço Android em estado simulado; opções ficam desligadas por omissão. |
| R6.5 `acc -f` | Contrato dos perfis preservado | Carregamento temporário não perde a configuração original; interrupção/reboot/unplug têm comportamento definido; `-a` tem resultado verificável. |

O placeholder webroot KSU não integra este roadmap. Uma WebUI funcional, suspensão de processos térmicos, defaults upstream impostos sobre perfis existentes e código revertido têm decisão de não integração no catálogo.

## R7 Validação e release

Cada incremento do motor executa testes de comportamento e sintaxe no shell Android. As alterações na app executam os testes relevantes, build e lint. Um candidato conjunto passa a suite completa e a matriz física abaixo.

| Cenário | Evidência necessária |
| --- | --- |
| Upgrade desde v1.0.3-ng | Backup verificado, configuração/perfil/avisos preservados e um daemon |
| Instalação limpa | Defaults explícitos, inicialização observável, sensores descobertos e calibração sem bloqueio |
| Reinício do daemon e reboot completo | Limites aplicados e controlo ativo sem desbloquear manualmente; sem processo duplicado |
| USB desligado, ligado e religado | Estado físico e eventos coerentes; sensores desconhecidos não geram unplug fictício |
| Pausa e retoma por capacidade | Corrente real confirma o efeito; perfil original restaurado após o ensaio |
| Temperatura/cooldown | Fixtures verificam todas as fronteiras e interrupção; não aquecer deliberadamente bateria nem falsear sensor físico |
| mcc/mcv/tl e controlo OEM | Escritas/aplicação e restauração confirmadas, sem retirar controlo térmico ao sistema |
| Switch automático e manual | Escolha, test report, suporte idle e retries corretos; ficheiro ausente não gera alegação de sucesso |
| Flags, workers órfãos e recovery | Esperas limitadas, sinais apenas aos processos corretos e rollback preserva configuração |
| Perfis, scripts e DJS | Aplicação/readback, configuração temporária e horários existentes conservados |
| Magisk, KSU e APatch | Resultados separados por gestor; o Samsung/Magisk não comprova os outros |

Regenerar o bundle através de `tools/build_ng.py`, comparar hashes, atualizar `Acc.bundledVersion`, assets do motor/installer, checksums, referências de fonte em F-Droid, documentação, changelog e validação. O certificado de assinatura permanece o estabelecido. Não marcar como testado em todos os aparelhos por passar no Samsung.

Um incremento só fica concluído quando os seus critérios passam. Novos candidatos de controlo de hardware podem ficar experimentais se não houver aparelho disponível; essa limitação deve acompanhar a release. R6 pode continuar pendente sem bloquear as correções do núcleo.

## Próxima sequência após I3

1. Completar resets/reaplicação reais de mcc/mcv/tl num kernel com controlos graváveis, controlo OEM e rollback. O upgrade I3 e a recusa de corrente já estão registados; USB e locked boot anteriores continuam a ser evidência de I2. Corrigir o cleanup de staging identificado em R5.3 antes de distribuição.
2. Completar R2.2/R2.3: novos switches/regras OEM e descoberta de sensores, com unidades e fontes identificadas.
3. R3: testes de switches, seleção/recuperação com orçamento, idle e estados térmicos/cooldown.
4. R4 e R5 pelo quadro de dependências: escrita/migração/import atómicos, DJS, readiness, root managers, instalação/recuperação e diagnóstico.
5. Fechar R7 e publicar um candidato conjunto a partir dos commits fixados. R6 continua opcional.

I3 não altera o schema nem migra perfis. O lock comum cobre os writers do frontend acca e a aplicação/seleção automática do daemon; concorrência global de configuração, imports, CLI legado e migração atómica continuam R4.
