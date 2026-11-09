# Safe Sailor

Bloqueador de sites para Android: simples, privado e bonito. Bloqueia os sites que
você escolher e, por padrão, conteúdo pornográfico.

**[⬇️ Baixar APK (v1.1.2)](apk/SafeSailor-1.1.2.apk)** · Android 8.0 ou superior (32 e 64 bits)

Também existe a [edição completa](#edições), com proteção contra desinstalação.

> Vindo da v1.0.0? Desinstale-a antes: a partir da v1.0.1 o app usa outra chave de assinatura. Da v1.0.1 em diante, basta instalar por cima.

| Início | Modo escuro | Sites | Atividade | Ajustes |
|---|---|---|---|---|
| ![](docs/screenshots/inicio-claro.png) | ![](docs/screenshots/inicio-escuro.png) | ![](docs/screenshots/sites.png) | ![](docs/screenshots/atividade.png) | ![](docs/screenshots/ajustes-escuro.png) |

## Recursos

**Bloqueio**
- Bloqueie qualquer site digitando o endereço ou colando um link (subdomínios inclusos).
- Pacotes prontos: redes sociais, apostas, jogos online, vídeos e streaming.
- Filtro de conteúdo adulto com cerca de 77 mil sites, já embutido no app e atualizável pela internet.
- Filtro por palavras-chave ("porn", "xxx"…) para pegar sites novos.
- Busca segura forçada no Google, Bing e DuckDuckGo, mais o modo restrito do YouTube (opcional).
- "Impedir desvios": bloqueia o DNS seguro (DoH) dos navegadores, que passaria por cima do filtro.
- Lista de permitidos para liberar um site bloqueado por engano.
- Escolha do servidor DNS: Cloudflare Família (padrão), AdGuard Família, CleanBrowsing, Cloudflare, Google ou Quad9.

**Qualidade de vida**
- Modo escuro (sistema, claro ou escuro) e cores Material You opcionais.
- PIN: desligar a proteção, trocar ou remover o PIN e desinstalar sempre pedem o PIN. Outras ações que afrouxam a proteção aceitam o PIN digitado nos últimos 2 minutos, com o app aberto. Depois de 5 erros seguidos, novas tentativas ficam bloqueadas por 30 segundos.
- Proteção contra desinstalação, na [edição completa](#edições).
- Aviso e botão "Religar" se a VPN for desconectada fora do app.
- Atalho nas configurações rápidas (bloco "Safe Sailor").
- Religa sozinho ao ligar o celular e depois de atualizar o app.
- Compatível com "VPN sempre ativa" do Android.
- Tela de atividade: veja o que foi bloqueado e bloqueie ou libere com um toque.
- Estatísticas: bloqueios de hoje, total e consultas.
- Importar e exportar listas (.txt, inclusive no formato hosts).

## Como funciona

O app cria uma **VPN local** que só recebe as consultas de DNS do aparelho; o resto
do tráfego não passa por ele. Quando um endereço está bloqueado, o app responde
"domínio inexistente" e o site não abre. Os demais endereços são consultados no
servidor DNS escolhido. Nada é enviado a servidores do Safe Sailor, e o registro de
atividade fica apenas na memória do aparelho.

**Limitações:** apps com VPN própria, ou navegadores com DNS seguro configurado
manualmente, podem passar por cima do filtro. Só pode haver uma VPN ativa por vez no
Android. Para máxima proteção, defina um PIN e ative "VPN sempre ativa" e "Bloquear
conexões sem VPN" em *Configurações → Rede → VPN → Safe Sailor*.

## Edições

| | Padrão | Completa |
|---|---|---|
| Arquivo | [`SafeSailor-1.1.2.apk`](apk/SafeSailor-1.1.2.apk) | [`SafeSailor-1.1.2-completa.apk`](apk/SafeSailor-1.1.2-completa.apk) |
| Todos os filtros, PIN e demais recursos | ✅ | ✅ |
| Proteção contra desinstalação | — | ✅ |
| Instala direto pelo navegador | ✅ | Exige desativar o Play Protect na instalação |

As duas edições são o mesmo app, com o mesmo pacote e a mesma assinatura. Você pode
instalar uma por cima da outra sem perder listas e configurações.

**Por que a edição completa é separada?** A proteção contra desinstalação usa um serviço de
acessibilidade. No Brasil e em outros países, o Google Play Protect **bloqueia
automaticamente** a instalação de apps baixados da internet que declaram esse tipo de
serviço. A mensagem é "App bloqueado para proteger seu dispositivo".

**Como instalar a edição completa:**
1. Abra a Play Store, toque na sua foto e vá em **Play Protect**.
2. Toque na engrenagem ⚙ e desative **Verificar apps com o Play Protect**.
3. Instale o `SafeSailor-…-completa.apk`.
4. Volte e reative a verificação do Play Protect.

Em celulares Samsung com Android 14 ou superior, confira também se o **Bloqueador
automático** está desligado, em *Configurações → Segurança e privacidade*. Ele
impede qualquer instalação de APK fora das lojas oficiais.

## Proteção contra desinstalação

*Somente na edição completa.*

O Android não permite que um app comum bloqueie a própria desinstalação. Como
alternativa, o Safe Sailor oferece um **serviço de acessibilidade** opcional (o mesmo
recurso usado por apps de controle parental). Com ele ativo e um PIN definido, as telas do
sistema que desinstalam, forçam a parada, limpam os dados, desconectam a VPN ou desligam o
próprio serviço são fechadas, e o app pede o PIN. Com o PIN correto, você tem 2 minutos
para fazer a alteração.

O serviço só olha as telas dos apps de sistema (Configurações, instalador de pacotes e
diálogos de VPN) e só age quando a tela menciona o Safe Sailor. Nada é guardado nem enviado.

Para ativar, vá em *Ajustes → Proteção contra desinstalação*. No Android 13 ou superior,
apps instalados por APK precisam antes de "Permitir configurações restritas", no menu ⋮
de *Informações do app*.

**Limites:** quem tiver acesso a um computador com depuração USB (ADB) ou souber
reiniciar o celular em modo de segurança ainda consegue remover o app.

## Compilar

Requisitos: JDK 17+ e Android SDK (plataforma 35).

```bash
./gradlew testPadraoDebugUnitTest   # testes
./gradlew assembleRelease           # as duas edições em app/build/outputs/apk/{padrao,completa}/release/
```

### Assinatura

A chave de assinatura **não fica no repositório**. Sem ela, o `assembleRelease` assina
com a chave de debug (serve só para testes locais).

**Local:** crie um `keystore.properties` na raiz (já está no `.gitignore`):

```properties
storeFile=/caminho/para/safesailor-release.jks
storePassword=...
keyAlias=safesailor
keyPassword=...
```

**GitHub Actions:** o workflow `.github/workflows/build.yml` roda os testes, compila
e assina o APK a cada push. Ele também publica uma Release quando você cria uma tag
`v*`. Configure em *Settings → Secrets and variables → Actions*:

| Secret | Valor |
|---|---|
| `KEYSTORE_BASE64` | `base64 -w0 safesailor-release.jks` |
| `KEYSTORE_PASSWORD` | senha da keystore |
| `KEY_ALIAS` | `safesailor` |
| `KEY_PASSWORD` | senha da chave |

Guarde a chave em local seguro: sem ela não é possível publicar atualizações que
instalem por cima da versão anterior.

### Atualizar a lista adulta embutida

```bash
scripts/update_adult_list.sh
```

## Estrutura

```
app/src/main/java/com/safesailor/
├── vpn/     VpnService, análise de pacotes IP/UDP/DNS, regras de filtro, bloco rápido, boot
├── guard/   regras da proteção contra desinstalação

app/src/completa/   arquivos só da edição completa (serviço de acessibilidade)
├── data/    configurações, listas, estatísticas, registro, lista adulta
├── ui/      Jetpack Compose: telas Início, Sites, Atividade e Ajustes
└── util/    normalização de domínios
```

## Créditos

Lista de conteúdo adulto: [StevenBlack/hosts](https://github.com/StevenBlack/hosts),
extensão "porn" (licença MIT).
