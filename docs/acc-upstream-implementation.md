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
