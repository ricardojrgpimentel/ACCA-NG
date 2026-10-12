# Registo de implementação do ACC dev

Este ficheiro acompanha cada incremento juntamente com o [roadmap](acc-upstream-roadmap.md) e o [catálogo dos 202 commits](acc-upstream-commits.md). A decisão da revisão indica o que aproveitar; o estado indica quanto já foi implementado. Um port adaptado não equivale ao cherry-pick integral do commit de origem.

## I1 — resets, permissões e store_mode

9 de outubro de 2026. **I1 integrado no código e no bundle candidato; fonte do motor publicada em [`7375bbb`](https://github.com/ricardojrgpimentel/ACC-NG/tree/7375bbb4be64b76643850bb15e56fcbc4e3ae6c8). Release e validação física pendentes.** Candidato ACC-NG **v1.0.4-ng (202610094)**, sobre NG `3d2dbe2`. A app parte de `38240f5`. O upstream revisto continua `908a5a4`; não se declara esse ramo como integrado na totalidade.

| Origem upstream | Estado | Comportamento integrado | Restante |
| --- | --- | --- | --- |
| `39a2208` | Aplicado com adaptação | Reset de corrente, tensão e temperatura apenas de controlos alterados pelo NG; não repetir após sucesso | Validação física de sysfs/política térmica OEM |
| `4b1bfd4` | Aplicado com adaptação | Ausência de personalização não força defaults; snapshot do valor real anterior, conservado entre aplicações/restarts no mesmo boot | Validação física de upgrade e restauração; descoberta/cache global permanece R1.2 |
| `eaa0c4d` | Parcial | Setters de corrente/tensão não executam hooks nem aplicam outros limites; hooks mantêm o fluxo próprio | Restantes otimizações do loop não portadas |
| `4999c63` | Aplicado com adaptação | Retirar chown/0644 forçados; escrita direta, fallback u+w limitado e restauração do modo original | Ensaiar permissões de sysfs e atualização pelo serviço OEM |
| `69cfecb` | Aplicado no escopo NG | Comentar battery/store_mode na base de candidatos automáticos | Metadata upstream substituída pela versão NG; outras exclusões continuam pendentes |

### Alteração do motor

`install/limit-control.sh` guarda os valores anteriores em `.mcc-custom`, `.volt-custom` e `.tl-custom`, dentro do diretório runtime volátil. O registo de ownership é adquirido após uma escrita confirmada ou mudança observada; uma escrita recusada sem efeito não cria ownership. Se o nó mudar mas o readback não confirmar o valor pedido, a operação falha e o snapshot fica disponível para recuperação. Falhas de reset mantêm as entradas por restaurar. Saída/interrupção durante uma escrita também conserva o valor anterior quando o ficheiro mudou.

As operações do mesmo tipo de limite usam um lock com espera máxima de cinco segundos. O descriptor é passado por stdin, como exigido pelo mksh, e o timeout envolve o flock compatível com BusyBox. As rotinas não herdam o trap de saída do daemon. Este incremento não garante recuperação perante SIGKILL ou perda abrupta do runtime; reboot, upgrade e rollback pertencem à validação restante.

`set_ch_curr()` e `set_ch_volt()` aplicam apenas o seu limite e propagam falhas das rotinas. Pedir corrente/default sem ownership não inicia descoberta nem espera por um carregador. A leitura da corrente configurada também não faz descoberta. `set_temp_level(0)` repõe o valor OEM capturado, que pode ser diferente de zero/100, e deixa de o escrever depois do reset concluído.

`write()` deixa de mudar proprietário/permissões de forma permanente. As escritas repetidas continuam, com falhas propagadas. O daemon restaura os controlos térmicos que alterou quando termina normalmente. `applyOnBoot`/`applyOnPlug` explícitos conservam a sua execução; os resets de limites deixam de passar por esses hooks.

Ficheiros de implementação no repositório `../acc-ng`: `install/limit-control.sh`, `misc-functions.sh`, `set-ch-curr.sh`, `set-ch-volt.sh`, `batt-interface.sh`, `accd.sh`, `ctrl-files.sh`, `tests/test_limits.py`. A versão, changelog e registo do motor foram atualizados.

SHA-256 do tarball candidato: `30d15c078fc5f4af0544c837681e37d6fc92be2f11b3f45ba88c448754756b2a`. O mesmo valor está em [bundled-source-sha256.txt](bundled-source-sha256.txt).

### Alinhamento com a app

- Bundle candidato regenerado com `python3 tools/build_ng.py`; `Acc.bundledVersion=202610094` e checksum atualizados.
- `ngApiVersion=1`, `configVerCode=202310160`, array capacity de seis elementos, calibração manual e sufixo térmico `r` preservados.
- `batt-info.sh`, `print-config.sh`, `default-config.txt` e `write-config.sh` conservam o conteúdo anterior; os parsers da app continuam a usar o contrato existente.
- O installer da app conserva as adaptações ACC/DJS e seleção estrita NG. Não foi substituído pelo installer dedicado apenas ao motor.
- O build escreve metadata candidata em `_builds/`; o anúncio público `module.json` conserva a última release publicada. `--update-metadata` é reservado à preparação da publicação.
- README/metadata corrente identificam o candidato; releases e validações históricas conservam as referências anteriores. O registo F-Droid aponta para o commit imutável do novo candidato.

### Validação

| Verificação | Resultado / alcance |
| --- | --- |
| Suite host do motor | 35 casos: 33 passaram; 2 testes de lock real reservados ao Android |
| Suite de limites no Samsung SM-G975F | 20 casos passaram com root, mksh e BusyBox; ficheiros temporários, sem escrever em sysfs real |
| Sintaxe Android | 28 scripts runtime passaram `/system/bin/sh -n` |
| Testes da app | 77 testes passaram, sem falhas ou skips |
| Build e lint | `testDebugUnitTest`, `lintDebug` e `assembleDebug` passaram; APK debug candidato gerado |
| Build determinístico | Tarball reproduzido com o mesmo SHA-256 e conteúdo runtime comparado com a fonte |
| Dispositivo instalado | Módulo, configurações, perfis e APK instalados não foram atualizados neste incremento |

Os testes cobrem resets sem personalização, isolamento dos setters/hooks, valor original ao reaplicar, thermal levels/siop, falha e aplicação parcial, readback recusado, escrita interrompida, permissão/restauração, traps, concorrência e lock limitado. O host usa shims para ferramentas macOS; o Android usa as ferramentas reais. A exclusão da lista store_mode também é verificada.

Comandos reproduzíveis no motor:

```sh
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tests -v
ACC_TEST_ADB_SERIAL=<serial> PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tests -p test_limits.py -v
python3 tools/build_ng.py
```

O runner Android requer root e BusyBox instalado. Usa apenas diretórios gerados em `/data/local/tmp`, removidos após cada fixture. As verificações atuais não comprovam funcionamento dos nodes de outro kernel, permissões de sysfs ou recuperação de uma instalação real.

### Próximo trabalho

Completar fixtures de output/parsing, perfis e sensores (R0); rever descoberta/cache (R1.2); alinhar suporte/aplicação física na confirmação da app (R1.4); completar blacklist e unificar os leitores de alimentação (R2.1/R2.4). Antes de distribuir I1, validar upgrade, reset/reaplicação de limites, política OEM, rollback e reboot num aparelho adequado. Não marcar R1/R2 ou a release como concluídas apenas por estes testes passarem.

Para cada incremento seguinte, acrescentar uma entrada com origem, escopo aplicado, partes pendentes, ficheiros, versão/checksum e evidência. Atualizar a coluna Estado do catálogo e a tabela de tarefas do roadmap no mesmo conjunto de alterações.

## I2 — R0: contrato e fixtures entre app/motor

9 de outubro de 2026. **Implementado no código e no bundle candidato v1.0.5-ng (202610095); sem release; instalado e parcialmente validado no Samsung, ver [validação física](acc-ng-validation.md#i2-physical-debug-check--samsung-sm-g975f-2026-10-09).** Fonte do motor: [`2d969cb`](https://github.com/ricardojrgpimentel/ACC-NG/tree/2d969cb3ec3854155479115b5b230d344e4ff44f). O incremento parte de I1 `7375bbb`; o upstream revisto continua `908a5a4`. O R0 valida a interface que temos de preservar para os próximos ports, sem declarar novos commits upstream como integralmente integrados.

| Tarefa | Implementação e evidência |
| --- | --- |
| R0.1 | Fixtures partilhadas de `-v`, estado do daemon (0/8/9 e falhas), `-i`, defaults/perfis de `-sp`, lista de switches e resultados de teste. Produzidas com fragmentos shell reais; os parsers usados pelos comandos da app leem as mesmas fixtures. |
| R0.2 | `ngConfigSchema=202310160` e `ngCapabilities` no módulo; metadata API 1 antiga usa o contrato base, sem inventar capacidades. API/schema desconhecidos não escolhem um handler de configuração presumido. Editor e handler usam a semântica térmica do contrato. |
| R0.3 | Perfil JSON legado, serialização de perfil atual, escrita/reload raw, pausa 60/resume 50, shutdown incluindo -1, cooldown/reset, r e switch manual. O motor preserva seis elementos de capacity, polaridade, idleThreshold e preferências de avisos/idioma. |
| R0.4 | Harness host e Android: mA/µA, mV/µV, corrente positiva/negativa/zero, sensores ausentes/inválidos/leitura recusada e alimentação USB/DC/wireless/OEM. Só escreve em diretórios temporários. |

### Correções reveladas pelos testes

- Corrente/tensão ausentes ou inválidas deixam de gerar medições zero e potência derivada no motor. Zero válido continua presente. A saída mantém nomes KEY=VALUE e unidades A/V/W normalizadas. Potência raw do uevent não compete com a potência normalizada.
- O parser da app usa a última leitura normalizada quando o output verbose também contém valores raw. Rejeita prefixos numéricos inválidos; conserva temperatura negativa, nomes e estados relevantes.
- Flags de bateria usam 1 para verdadeiro; CURRENT_QNOVO e CHARGE_DISABLE leem os seus campos próprios.
- Switch e hooks aceitam aspas envolventes e CRLF; hashes dentro de quotes e argumentos shell literais são preservados. Um ` --` noutro comando não força o switch. Hooks são enviados como um único argumento sem expansão prematura no shell da app.
- Parsing de versão, estados e resultados usa funções comuns aos comandos reais e aos testes. Uma probe falhada/incompleta não confirma suporte idle.

### Validação

| Verificação | Resultado / alcance |
| --- | --- |
| Suite host do motor | 52 testes: 50 passaram; 2 testes de lock real reservados ao Android |
| Contrato no Samsung SM-G975F | 17 testes passaram com root, mksh e BusyBox; ficheiros temporários |
| Suite de limites no Samsung | 20 testes passaram; ficheiros temporários, sem sysfs real |
| Sintaxe Android | 28 scripts runtime passaram `/system/bin/sh -n` |
| App | 106 testes unitários passaram em debug e release; build e lint de ambas as variantes passaram. Release local sem assinatura, como no CI; não publicada. |
| Sincronização | Fixtures exportadas nos dois projetos; CI verifica metadata e SHA-256 dos scripts de origem contra o bundle da app |
| Build determinístico | Tarball reproduzido com o mesmo SHA-256: `b6b7dca9b4fd7816031b7852ed66ef339626d5c76d4f22c71dc5d130ebecca4a` |
| Dispositivo instalado | Candidato posteriormente instalado; upgrade, reinício e pausa/retoma com switch conhecido passaram. Ver registo de validação física |

As fixtures executam funções/dispatch relevantes com hardware/serviço substituídos por ficheiros e stubs. Não são uma instalação completa nem uma prova de sysfs OEM. Mantêm a precisão atual do output (duas casas A/V/W); descoberta de unidades e precisão adicional ficam em R2. A migração atómica, import de scripts arbitrários e lifecycle real permanecem R4/R7.

Reproduzir no motor:

```sh
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tests -v
python3 tools/export_contract.py --check --app ../ACCA-NG
ACC_TEST_ADB_SERIAL=<serial> python3 -m unittest discover -s tests -p test_contract.py -v
```

Na app: `python3 tools/check-engine-contract.py` seguido de `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`. O candidato continua sem publicação; module.json conserva v1.0.3-ng. A próxima release da app precisa de novo versionCode/versionName antes de gerar/publicar um APK alterado.

### Próximo incremento

R1.2: descoberta/cache dos controlos e exposição de suporte. R1.4: distinguir configuração gravada de aplicação física. R2.1/R2.4: blacklist e semântica comum de alimentação entre controlador, eventos e app. Validação física parcial de I1/I2 registada: upgrade, reinício e pausa/retoma com switch conhecido passaram. Deteção física de desligar/religar USB também passou. Arranque pelo Magisk antes do primeiro desbloqueio também passou, preservando a configuração. Resets/reaplicação de potência, OEM e rollback permanecem pendentes.


## I3 — descoberta, alimentação e aplicação de limites

10 de outubro de 2026. **Implementado no candidato ACC-NG v1.0.6-ng (202610106) / AccA-NG 2.0.0-ng-beta.5 (45); motor e app debug instalados e parcialmente validados no Samsung.** Fonte do motor: [`1e8291b`](https://github.com/ricardojrgpimentel/ACC-NG/tree/1e8291bcc69d59de677674728075b0c9063b561a), sobre `2d969cb`; base da app `6b52e0a`. API NG 1, schema 202310160, capacity de seis elementos, calibração, r e perfis conservados. Release pendente.

| Tarefa/origem | Comportamento integrado | Restante |
| --- | --- | --- |
| R1.2 — 07a3a8d/e89c463 | Cache por tipo/boot, valores inteiros estritos, unidades mA/µA e mV/µV, aliases deduplicados; reconstrução conserva snapshot original. Cache vazio representa probe concluída. Alteração de currentWorkaround refaz a seleção sem fallback. | Descoberta de sensores alternativos continua R2.3; ensaio OEM real pendente. |
| R1.4 — 486aa90/a74d83b | Pedidos offline são guardados sem espera. acca --power-status expõe pedido, suporte e estado; aplicado exige tentativa bem sucedida e readback dos controlos. Drift/readback recusado são falha. App distingue configuração gravada da aplicação do limite. | Não mede a eficácia elétrica do limite; essa confirmação exige ensaio físico. |
| R2.1 — d8cf7bb/e00ba35/67ec354/52265a1/7784280/6b308d8 | Blacklist final no parser e validação de candidatos, logs antigos e referências de níveis térmicos; leitura recusada/vazia/inválida não autoriza escrita. Descoberta não altera permissões. | Novos switches/exceções OEM continuam R2.2. |
| R2.4 — deteção NG / parte de 9cdf64f | external-power.sh é a fonte comum do controlador, eventos e código Kotlin gerado. Connected prevalece; uma fonte inválida junto de fontes offline mantém unknown. Battery/BMS/OTG são excluídos por nome/tipo. | Polaridade automática e funcionalidades misturadas de 9cdf64f continuam R3/R6. |

A aplicação do daemon lê a configuração mais recente sob o lock do frontend e deixa de enviar setters assíncronos que regravavam perfis antigos. Guardar um switch automático relê o config e respeita uma escolha manual entretanto feita. Scripts explícitos applyOnBoot/applyOnPlug conservam execução própria. O lock não substitui a escrita/migração atómica de R4 nem cobre todos os writers legados.

Os valores originais de todos os controlos são capturados antes da primeira escrita: dois ficheiros distintos podem partilhar o mesmo valor no driver. As fixtures verificam que a escrita no primeiro não contamina o snapshot do segundo.

`power-limits-status` é uma capacidade aditiva, sem alterar outputs existentes de -i/-sp. A leitura não descobre nem escreve nodes: um writer ocupado ou protocolo incompleto resulta em indisponível. A app apresenta os estados no resultado de guardar definições/perfis e no diagnóstico; só confirma o pedido correspondente. Motor antigo sem capacidade mantém confirmação da configuração e não inventa readback físico. Os textos novos têm pt-PT/pt-BR e fallback inglês para as restantes línguas.

Ficheiros principais do motor: control-discovery.sh, control-write.sh, external-power.sh, ng-power-limits.sh, setters, acca/accd, misc-functions, batt-interface, ng-events e parser de switches. A app inclui PowerLimits, PowerLimitsText, GeneratedExternalPower, SharedViewModel, resultados/diagnóstico e fixtures partilhadas. O exportador gera o helper Kotlin; CI verifica-o contra o código efetivamente empacotado.

### Validação I3

Os resultados finais e checksum constam da [validação I3](acc-ng-validation.md#i3-local-candidate--2026-10-10). As suites usam ficheiros temporários. No ensaio físico, o upgrade para I3 preservou a configuração e arrancou um único daemon. Os nodes de corrente recusaram 250 mA e o readback reportou falha; o pedido de 4100 mV ficou pendente durante cooldown. Os pedidos foram retirados, a configuração original reposta e a carga retomou. Aplicação/restauração efetiva de limites, política OEM e matriz completa R7 continuam pendentes. A matriz física I2 não foi promovida a prova de I3.

A primeira tentativa de upgrade revelou que o cleanup herdado apaga a origem quando o staging corresponde a /data/local/tmp/acc[-_]*. A recuperação automática repôs I2 e o config. Repetir fora desse padrão permitiu instalar I3; a correção do cleanup continua R5.3.

### Seguinte

Completar a aplicação/restauração física de limites num kernel com controlos graváveis; seguir R2.2/R2.3 e R3 e corrigir o cleanup de R5.3. A origem do bundle está fixada no commit do motor; concluir R7 antes de distribuição. R4/R5 continuam abertos; R6 é opcional.


## I4 — preservação do staging de instalação e recuperação

11 de outubro de 2026. **Candidato ACC-NG v1.0.7-ng (202610117) / AccA-NG
2.0.0-ng-beta.6 (46), sem publicação.** Fonte local do motor fixada em
`8305720c356fec9c8b4be8ee7a5de1bffc4983aa`, sobre I3 `1e8291b`;
base da app `e857f57`. A fonte I4 ainda não foi enviada ao remoto.

R5.3 parcial: removido o glob /data/local/tmp/acc[-_]* de install/uninstall.sh.
O desinstalador remove os caminhos instalados; staging e ficheiros temporários
continuam sob responsabilidade de quem lançou a instalação. Os instaladores
tarball/online conservam a limpeza da extração no fim, depois da recuperação.
É uma correção NG da falha observada em I3; não declara novo commit upstream
como integralmente portado. API 1, schema 202310160 e controlo de carga mantidos.

Seis testes de regressão executam nove cenários com os scripts completos em
filesystem temporário: staging com hífen, underscore e caminho aninhado,
instalação limpa, desinstalação completa, tarball e falha de cópia com exit 42.
Verificam preservação da origem e de ficheiros alheios, backup/configuração,
restauro do módulo anterior, inicialização única do serviço simulado e
propagação do erro antes de limpar a extração. Todos os seis falham com o
código anterior e passam no host e no shell Android. Os comandos de processos,
root e serviço são simulados; não exercitam hardware. A proveniência partilhada
passa de 18 para 22 hashes, incluindo os quatro scripts de instalação.

O bundle foi reproduzido a partir de um git archive limpo do commit fixado;
o desinstalador flashable contém exatamente a fonte corrigida. A app empacota
o candidato, atualiza bundledVersion e fixtures e reserva beta.6/code 46.

Validação: 79 testes do motor (77 passam no host, dois exclusivos Android
omitidos; 79 passam com runners Android), 35 scripts com sintaxe Android válida,
114 testes da app em cada variante debug/release, builds e lint de ambas as
variantes. Upgrade real no Samsung a partir de staging acc-* com o instalador
exato da app passou, preservou o hash do config e terminou com um daemon
correspondente ao lock. A app debug beta.6 foi instalada. Ver [evidência e
checksum](acc-ng-validation.md#i4-staging-cleanup-candidate--2026-10-11).

Restante: rollback CLI explícito, restante cleanup/caminhos NG, KSU/APatch e
matriz R7. A recuperação após falha de cópia foi exercitada em fixtures; o
ensaio físico I4 foi um upgrade bem sucedido. Limites/OEM, USB e locked boot
não foram retestados. O próximo incremento de código é R2.2/R2.3.


## I5 — R2 sensores físicos e switches OEM (incremento de trabalho)

11 de outubro de 2026. Código integrado na branch do roadmap, sem novo bundle
ou instalação física. O candidato instalado permanece I4 até à validação e
empacotamento conjuntos dos grupos restantes. Fonte/testes em acc-ng; inclui
ASUS, FP5/qcom, bypass, regras OEM exatas e preservação de escolhas manuais.
Descoberta aceita Nexus 10 com status/capacidade separados e ausência de uevent;
identifica fonte/escala e distingue corrente de entrada da corrente da bateria.
Dados ausentes não recebem valores fictícios; perda de telemetria de proteção
pausa o controlo escolhido com diagnóstico, sem sondagem nem shutdown falso.
--sensor-status é aditivo; diagnóstico da app preserva compatibilidade antiga.
91 testes do motor passaram com runners Android, incluindo 12 cenários de
sensores/OEM. Certificação física em outros OEM continua pendente.


## I6 — fecho do núcleo e candidato conjunto

11 outubro 2026. **ACC-NG v1.0.8-ng (202610118) / AccA-NG 2.0.0-ng-beta.7 (47)**. Fonte do motor `ea5972f5d95e08ba9b98ac67c83ef375121aad72`; bundle SHA-256 `96a4d95125be088cd5d2109020da8bcfaa8f1ddfb88f2359fed9922554ada1a1`. As notas históricas I1–I5 descrevem a situação desses incrementos; este registo atualiza o candidato final.

- R2: descoberta OEM separada, origem/escala da corrente, distinção bateria/entrada e pausa protetora com sensores ausentes. O helper partilhado mantém unknown quando a nossa própria pausa Samsung corta online; não inventa unplug/calibração.
- R3: probes ON/OFF com orçamento 45s, três amostras, códigos preservados em quiet, exclusão mútua e journals de restauro após TERM/KILL. Config nunca é revertido pelo teste. Seleção automática/erro 7 limitados, manual preservado, polaridade aprendida sem bloquear e proteção térmica/capacidade/cooldown consistente.
- R4: writers, reset e remoção CLI sob mutex/rename/backup; import parcial CRLF e scripts por nome, valores/quoting/extensões preservados. Reset fecha o lock antes de reiniciar daemon. Scheduler diário evita duplicados e resolve wrappers NG mantendo DJS.
- R5: engine-state/readiness sem espera em status, disable/boot limitados, flags explícitas Magisk/KSU/APatch e BusyBox independente; staging privado e arquivos validados, HTTPS/TLS/HTTP, rollback de snapshots NG e export limitado com contexto de sensores/teste/limites.
- App: reconhece initializing/sensor-wait/config-invalid, preserva códigos de teste incompleto, cita argumentos literalmente, termina testes modernos com restauro e conserva compatibilidade com motores antigos. Textos en/pt-PT/pt-BR.

Preservados API 1/schema 202310160, seis campos capacity, r, polaridade manual, preferências NG, perfis e DJS. Defaults/cooldownCustom permanecem NG; não usar 20 como fallback de node ilegível, inferir gestor por BusyBox ou executar bg-dexopt-job no loop.

Validação final: 133 casos do motor (131 passam host, dois Android-only; 133 passam Android), 53 scripts com sintaxe Android válida, 119 testes da app por variante debug/release, builds/lint e APK assinado/alinhado com o certificado existente. Git archive limpo reproduz o bundle. No Samsung passaram upgrade com staging/sentinela, 31 hashes runtime, teste do switch, carga/pausa/retoma, rollback para v1.0.7/reinstalação e reboot antes do PIN. Config/perfis/assinatura preservados; ensaio de boot usa uma histerese temporária afastada do limiar e repõe config/last-good. Ver matriz I6 em acc-ng-validation.md.

Limites: mcc/mcv/tl graváveis, política de outros OEM e KSU/APatch não são certificados pelo Samsung. USB desligado/religado não foi repetido fisicamente neste candidato. As novas idle_apps/Encore/percentagens de corrente R6 ficam opcionais fora do candidato; as opções existentes preservam fonte física e proteções. Fonte submetida em PRs de revisão; nenhuma release nem alteração do updater estável.

I6 adicional: helpers de temp_level/SIOP confirmaram no Samsung a escrita e o restauro 100→80→100 com mode 664/config preservados e carga pausada. Não certifica eficácia elétrica nem política térmica OEM sob carga; corrente/tensão continuam a exigir controlos graváveis. A CI do código candidato passou as verificações Android no GitHub.


## I7 — calibração após desligar o cabo

12 outubro 2026. **ACC-NG v1.0.9-ng (202610129) / AccA-NG beta.8 (48)**.
Fonte runtime `b3e59e6d3bed20d52aadcb66d351f4a7eac2af88`; bundle SHA-256
`6254bb8ad9bf5bf2d9e35c1344124815325258c1b62b63da7a5f9ddfa8682b51`.
O ensaio físico I6 revelou ON retries que atrasavam a calibração sem cabo.
I7 evita ON com input ausente, interrompe uma verificação em curso e conserva
Discharging mesmo sem polaridade aprendida. Input unknown preserva retoma OEM.
Regressões reproduzem 153→27 segundos para três amostras e 42→3 segundos para
ON pendente; 138 casos Android/136 host mais dois skips passaram. App: 119 testes
por variante, lint/build e APK assinado/alinhado; CI verde. Git archive limpo
reproduz o bundle, fixtures/31 hashes runtime e dados/prefs foram verificados.
No Samsung, a repetição física aprendeu polaridade negativa em 30 segundos e
regressou a Charging/+1647 mA. Config/last-good restaurados byte a byte, daemon
único/lock/ready confirmados. Ver [evidência I7](acc-ng-validation.md#physical-i7-calibration-and-restoration--2026-10-12).
Certificação mcc/mcv/outros OEM/KSU/APatch e R6 opcional continuam separadas;
nenhuma release/updater foi publicada.
