# Revisão do ACC dev para integração no ACC-NG

9 de outubro de 2026. A integração recomendada é seletiva, por comportamento final, preservando a interface do ACC-NG com a app. Há melhorias relevantes em gestão de corrente e tensão, descoberta de controlos, testes de switches, configuração e recuperação. Um merge integral também introduziria incompatibilidades e comportamentos que precisam de correção.

O [roadmap](acc-upstream-roadmap.md) define a implementação nos dois repositórios. O [catálogo dos commits](acc-upstream-commits.md) atribui uma decisão aos 202 commits. As decisões são propostas de integração; não significam que o código já foi integrado ou validado em hardware.

## Referências e alcance

| Referência | Commit |
| --- | --- |
| Base comum ACC v2023.10.16 | `f3d6eb0f5697c3d2e401097e9b5b6b8370bac1b2` |
| ACC dev revisto | `908a5a4b716b7f2506a2e1426429293a11f42ab3` |
| ACC-NG main revisto | `3d2dbe2` |
| AccA-NG main revisto | `3d6a223` |

O `git ls-remote` confirmou o ramo remoto: o último commit de `dev` é de 5 de junho de 2025. A comparação `main...upstream/dev` no motor resulta em **4 commits exclusivos NG e 202 exclusivos upstream**. O delta final upstream altera **45 ficheiros**, dos quais dois novos: `install/android.sh` e `install/webroot/index.html`. A metadata de `dev` continua a declarar `v2025.5.18-dev`, mesmo com commits posteriores.

A revisão cobre os patches de shell, as alterações de configuração, ferramentas de build/push, o delta final de documentação e traduções e os contratos atuais da app. `install.sh`, `customize.sh` e `META-INF/com/google/android/update-binary` têm o mesmo blob no estado final de cada motor; devem continuar sincronizados. README HTML e ZIP de desinstalação são derivados: devem ser regenerados da fonte NG, não copiados como binários opacos. A tradução chinesa extensa deve conservar atribuição e ser revista contra a ajuda realmente implementada.

## Decisões por área

| Área | Mudança upstream | Alinhamento recomendado |
| --- | --- | --- |
| Corrente e tensão | Cache de descoberta, aplicação independente de `mcc`/`mcv`, marcadores de personalização e resets condicionais | Prioridade alta. Portar o resultado final, mantendo verificação de aplicação e evitando resets que contrariem o controlo térmico OEM. |
| Permissões de sysfs | A sequência `chown 0:0`, `chmod 0644`, escrita e `chmod 0444` termina substituída por `chmod a+w` | Adaptar. Preservar proprietário e modo original; não retirar acesso ao serviço OEM nem deixar escrita aberta a todos. O NG atual ainda herda `chown`/`0644`, mas não o `0444` intermédio de dev. |
| Base de switches | ASUS, FP5, Qualcomm, Oplus, Motorola e controlos alternativos; exclusão de `battery/store_mode` | Integrar exclusões e candidatos de forma separada. Distinguir os controlos experimentais e deduplicar a linha Oplus repetida. |
| Exceções OEM | MSM8916, MSM8937, CRO-L03, MSM8953 e MT6795 | Portar por condição exata de aparelho/board, preservando escolhas manuais e a configuração Samsung já validada. |
| Descoberta de sensores | Nexus 10, separação entre nó de capacidade e status, `rt*-charger/current_now`, sensores MediaTek | Adaptar seleção e unidades; não pressupor que corrente do carregador equivale à corrente da bateria. |
| Alimentação externa | Várias reescritas de `online()` acabam numa lista de nomes permitidos | Preservar a semântica NG de desconhecido. Unificar motor, eventos e `ExternalPowerSupply` da app; ampliar compatibilidade por tipo e testes. |
| Idle e testes | Limiar final de 10 mA, `_STI=35`, lista de switches que funcionaram, leitura de valor OFF de outro ficheiro | Integrar com adaptação do parser, códigos de saída, tempos limite e amostragem estável. `.testingsw` é um sinal de estado, não substitui um mutex. |
| Seleção automática | Tolerância a retomas inesperadas, rotação de switches e reinicialização após erro 7 | Adaptar com limite de tentativas e atraso. Evitar ciclos ilimitados de reinicialização. Preservar um switch forçado pelo utilizador. |
| Temperatura e cooldown | Estado `mtReached`, remoção do sufixo `r`, novos defaults e simplificação de sleeps | Separar melhoria do estado da alteração de contrato. Preservar perfis existentes e verificações periódicas de proteção durante cooldown. |
| Configuração | Import parcial, deduplicação de scripts, CRLF, ajuda por variável, limites e novo formato com aspas | Integrar com escrita atómica, validação, preservação de scripts e parsing compatível. Não usar defaults para substituir silenciosamente um perfil válido. |
| Nível e serviço Android | Preferência por nível do serviço Android, máscara de capacidade, wrappers `dumpsys`, reset de estatísticas | Separar nível físico do nível apresentado. Controlos e shutdown não devem depender de um nível mascarado ou simulado. Reset permanece opcional. |
| Arranque e recuperação | Espera por boot animation, flag persistente de disable, logs de testes e rollback | Adaptar à recuperação NG. Preservar locks com espera limitada e backups datados. A deteção automática por PID do zygote foi retirada antes do fim de dev. |
| CLI e agendamento | Sintaxe de `-f`, filtros de logs/info, notificações, scripts nomeados e scheduler `at()` | Portar compatibilidade sem reescrever quoting de comandos arbitrariamente. Os horários da app usam DJS, um fluxo distinto de `at()` do ACC. |
| Root e distribuição | Mais caminhos BusyBox, wrappers KSU/APatch, overlayfs, curl/wget | Integrar seletivamente, mantendo repositório/canal NG, identidade, instalação pela app e build determinístico. Não tornar TLS inseguro o default. |
| Funcionalidades opcionais | `idle_apps`, Encore e webroot KSU | `idle_apps` e Encore ficam opt-in após o núcleo; o webroot é apenas “Hello There!” e não acrescenta uma interface útil à app. |

## Incompatibilidades concretas com a app

### Saída de informação da bateria

`d3faa4f` passa a imprimir `current_now 0.75A`, `voltage_now 4.20V`, `level 60%` e `temp 30℃`. O handler atual `acc/v202107280/AccHandler.kt` procura `CURRENT_NOW=`, `VOLTAGE_NOW=`, `CAPACITY=` e `TEMP=`. Os quatro padrões falharam numa fixture com o formato final de dev. O novo output também deixa de expor vários campos de uevent que o modelo atual conhece.

Decisão: manter saída estável para `acca -i` no contrato NG 1. Melhorias de descoberta e cálculo podem entrar por trás dessa interface. Um output adicional pode ser acrescentado depois com capacidades explícitas e testes da app.

### Configuração e temperatura

`d31210f` reduz `capacity` de seis para cinco elementos e move `capacity_mask` de índice 5 para 4. Retira `capacity_sync`, `discharge_polarity` e `idle_threshold`. A nossa calibração escreve `discharge_polarity`, os diagnósticos leem `dischargePolarity` e `idleThreshold`, e `ng-events.sh` observa capacidade e temperatura do esquema existente.

`8af4502` elimina `resume_temp=...r`, enquanto `TemperatureConfig.command()` ainda o gera e os perfis conservam `resumeTemperatureOverridesCapacity`. Além disso, `write-config.sh` upstream força uma diferença máxima de 10 graus e pode alterar uma temperatura pedida pela app. A app apenas exige `resume < max`; uma normalização silenciosa pode causar divergência no readback e na identidade do perfil.

Decisão: manter inicialmente o esquema de seis elementos, a calibração manual e o sufixo `r`. Uma futura migração exige conversão explícita, compatibilidade dos perfis, backups e leitura coerente nos dois lados.

### Testes de bypass e formato de configuração

`fa93728` remove o tratamento de `-t --`; a app ainda o chama em `isBatteryIdleSupported()`. `ade10f1` altera a linha `- battIdleMode=...` para uma linha sem hífen; a regex atual exige o hífen. A saída de configuração passa a colocar aspas em `charging_switch` e `apply_on_boot/plug`; o parser atual pode conservá-las no valor e deixar de reconhecer o sufixo ` --`.

Decisão: manter aliases e output atuais ou atualizar motor e app juntos. O limite de 60 segundos da app tem de ser reconciliado com `_STI=35`, as verificações ON/OFF e os testes de vários switches.

### Inicialização e confirmação de configuração

`03bff24` acrescenta esperas de até 70 segundos a `acca`, antes do dispatch, incluindo comandos de estado. A app usa 5 segundos para ler o estado, 10 para iniciar e 20 como limite normal. Importar essa espera indiscriminadamente pode transformar “a inicializar” num erro ou bloqueio da UI.

`a74d83b` deixa o daemon resolver certos limites depois de `acca --set`; a nossa confirmação compara a configuração durante cerca de cinco segundos. É preciso distinguir configuração gravada de controlo realmente aplicado e suporte inexistente, sobretudo sem carregador ligado.

## Código que precisa de correção antes de integrar

| Evidência | Problema | Tratamento |
| --- | --- | --- |
| `872dd3d`, branch final de teste silencioso em `acca.sh` | `return $?` ocorre depois de `echo Ok/Idle/Fail`; perde o código do teste. Na fixture Bash, resultados 0, 15, 7 e 16 produziram todos retorno 0. | Capturar o código imediatamente e propagá-lo, incluindo Idle=15. Revalidar no shell Android. |
| `02863a4`, cooldown final | A espera de `cooldown_charge` perde o ciclo que verificava capacidade, temperatura e estado durante a espera. | Conservar espera interrompível. Uma redução de processos não justifica atrasar proteção quando os limiares são atingidos. |
| `4999c63`, permissões finais | `chmod a+w` concede escrita a todos e não restaura modo original. | Tentar escrita com permissões existentes; fallback mínimo, limitado e com restauração testada. |
| `9cdf64f`, `online_f()` final | Fontes fora da lista e ausência de dados produzem offline. | Preservar desconhecido. Fixture `vendor-charger/online=1` foi ignorada por dev e reconhecida por NG. |
| `16df5bf`, erro 7 | Reinicialização por `exec` sem orçamento explícito de tentativas. | Recuperação limitada, diagnóstico terminal claro e sem múltiplos daemons. |
| `c7e5250`, `5c3c916`, downloader final | `--insecure` e `--no-check-certificate` são usados automaticamente em dev. | Manter validação TLS e falha explícita. O fallback wget do nosso motor também herda `--no-check-certificate` e entra na revisão do downloader. |
| `9cdf64f`, `set_dp()` final | Uma amostra de corrente zero desativa `batt_status_workaround`; estado de carga sozinho decide a polaridade. | Exigir amostras estáveis, sensor válido, alimentação externa coerente e fallback manual preservado. |
| `09e38a4` e `908a5a4`, `pause_now()` | `idle_apps`/Encore alteram limiares de capacidade em memória para provocar pausa. | Modelar um motivo de pausa temporário sem mudar o perfil efetivo nem o readback. |

Os nomes dos commits nem sempre descrevem o resultado final. A suspensão térmica (`4023520`, `979b124`, `a193b31`, `5ce4ac9`, `94770b8`) foi eliminada por `54e159c`. A limitação forçada de corrente durante testes (`ed97a99`) foi revertida por `b6c9ecf`. O corte de timeout para cinco iterações terminou substituído por 35. Estes passos intermédios não devem ser ressuscitados.

## Alterações NG a preservar

- `id=acc`, paths existentes, pacote `com.accang.app`, canal de atualização e seleção de arquivo com marcador `ngApiVersion=1`.
- Backups datados, validação dos scripts antes da substituição e recuperação do módulo em falha de instalação.
- Paragem apenas de workers verificados e espera limitada pelo lock; dev ainda usa o caminho antigo baseado no PID do lock.
- Leitura de alimentação externa que não confunde `battery/online`, BMS e OTG com entrada de energia; dados desconhecidos suspendem calibração.
- Eventos observadores, notificações fora do loop com tempo limitado, log limitado, PT/EN e preferências globais preservadas ao aplicar perfis.
- Limites dinâmicos, pausa térmica, shutdown, escolha manual de switch e configuração já validada no Samsung.
- Build reproduzível com `tools/build_ng.py`; o build upstream usa timestamps do momento e não substitui esta ferramenta.

Há também alinhamento interno a fazer: `ng_online()` exclui nomes battery/BMS/OTG, mas não consulta `type` como o leitor principal e a app. Os três leitores devem partilhar a mesma semântica e fixtures. `AccDaemonProcess` reconhece `-t`/`--test` exatos, por isso tem de acompanhar a sintaxe `-t35` se a adotarmos.

## Cobertura dos 45 ficheiros do delta final

| Ficheiros upstream | Destino da revisão |
| --- | --- |
| `install/accd.sh`, `batt-interface.sh`, `misc-functions.sh` | R1 corrente/permissões; R2 sensores; R3 estados/proteções; R4 scripts; R5 recuperação; R6 opcionais |
| `install/set-ch-curr.sh`, `set-ch-volt.sh`, `read-ch-curr-ctrl-files-p2.sh` | R1; cache e ownership de limites, suporte e reset sem bloqueio |
| `install/ctrl-files.sh`, `oem-custom.sh` | R2; base de switches, exclusões e exceções OEM; migração em R4 |
| `install/acc.sh`, `acca.sh`, `batt-info.sh`, novo `android.sh` | R0 contrato; R3 testes; R4 CLI/config; R5 diagnósticos; R6 opções |
| `install/default-config.txt`, `print-config.sh`, `write-config.sh`, `set-prop.sh` | R0 contrato; R4 migração, scripts, defaults e quoting |
| `install/logf.sh`, `power-supply-logger.sh`, `strings.sh`, `wizard.sh` | R5 diagnósticos, códigos, ajuda e terminal; linguagem em R7 |
| `install/release-lock.sh`, `service.sh`, `uninstall.sh`, `setup-busybox.sh` | R5; preservar recuperação NG, alinhar root, flags e caminhos |
| `install.sh`, `customize.sh`, `META-INF/com/google/android/update-binary`, `install-online.sh`, `install-tarball.sh` | R5; instalação, rollback, TLS, identidade e sincronização |
| `build.sh`, `push.sh`, `module.prop`, `bin/acc_flashable_uninstaller.zip` | R5/R7; tooling de root, metadata NG e artefactos regenerados |
| `README.md`, `README.html`, `changelog.md` | R7; descrever apenas comportamento realmente integrado |
| `install/translations/de-DE/strings.sh`, `fr/strings.sh`, `id/strings.sh`, `pt-PT/strings.sh`, `tr/strings.sh`, `zh-rCN/strings.sh`, `fr/README.md`, `tr/README.md` | R7; conservar créditos, adaptar contrato NG e completar ajuda |
| novo `install/webroot/index.html` | Não integrar o placeholder; uma WebUI real seria outro trabalho |

## Validação da revisão

Os **14 testes atuais do motor passaram** com `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tests -v`. As reproduções de output, retorno do teste silencioso e alimentação externa foram isoladas, em Bash/ficheiros temporários. Confirmam os casos descritos; não substituem `/system/bin/sh`/BusyBox ou ensaios físicos.

O [registo existente](acc-ng-validation.md) documenta a validação anterior no Samsung SM-G975F. Não valida os novos patches upstream. Nesta revisão não foram alterados o motor, o bundle, a app nem o aparelho; a implementação e os seus critérios de saída estão no roadmap.

Para reproduzir o inventário no repositório do motor:

```sh
git rev-list --left-right --count main...upstream/dev
git merge-base main upstream/dev
git log --reverse --format='%H %ad %s' --date=short main..upstream/dev
git diff --name-status f3d6eb0f5697c3d2e401097e9b5b6b8370bac1b2 908a5a4b716b7f2506a2e1426429293a11f42ab3
git show <commit> -- <ficheiro>
```
