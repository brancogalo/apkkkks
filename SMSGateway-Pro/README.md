# 📱 SMS Gateway Pro

Um sistema profissional para gerenciar **1.000+ dispositivos Android** e receber SMS centralizados em um único dashboard web.

---

## 🎯 Características

✅ **Suporta 1.000+ dispositivos simultaneamente**
- Cada dispositivo com IP diferente
- Auto-descoberta via UDP broadcast
- Conexão persistente com WebSocket

✅ **Dashboard web em tempo real**
- Lista de todos os dispositivos conectados
- Mostra número de telefone de cada dispositivo
- Status online/offline
- SMS recebidos em tempo real
- Filtros por dispositivo

✅ **Backend robusto**
- Node.js + Express
- WebSocket para comunicação em tempo real
- SQLite para armazenamento
- API REST completa

✅ **App Android invisível**
- Sem ícone na tela inicial
- Auto-inicia ao ligar o telefone
- Silencia notificações de SMS
- Conexão automática ao servidor

---

## 🚀 Instalação Rápida

### 1️⃣ Configurar Backend (PC)

```bash
# Navegar para a pasta do projeto
cd SMSGateway-Pro

# Instalar dependências
npm install

# Iniciar servidor
npm start
```

Pronto! Servidor rodando em `http://localhost:3000`

### 2️⃣ Compilar App Android

#### Via Android Studio:
1. Abra Android Studio
2. `File` → `Open` → Selecione a pasta `SMSGateway-Pro`
3. Aguarde sincronizar
4. `Build` → `Build APK(s)` → `Build Release APKs`
5. APK pronto em: `app/build/outputs/apk/release/app-release.apk`

#### Via GitHub Actions:
1. Crie novo repositório no GitHub
2. Upload de todos os arquivos
3. Crie `.github/workflows/android-build.yml` com:
```yaml
name: Build APK
on: [push]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '11'
          distribution: 'adopt'
      - run: chmod +x gradlew && ./gradlew assembleRelease
      - uses: actions/upload-artifact@v4
        with:
          name: APK
          path: app/build/outputs/apk/release/app-release.apk
```

### 3️⃣ Instalar no Android

1. Pegue o arquivo `app-release.apk`
2. Envie para cada dispositivo Android
3. Clique para instalar
4. Dê permissão de SMS
5. Configure como app SMS padrão

---

## 📊 Dashboard

Acesse no navegador: **http://seu-ip:3000**

### Funcionalidades:

- **Dispositivos Conectados**: Lista todos os telefones conectados
- **Status em Tempo Real**: Online/Offline de cada dispositivo
- **Número de Telefone**: Mostra o número de cada dispositivo
- **SMS Recebidos**: Visualiza SMS de todos os dispositivos
- **Filtros**: Por dispositivo, por lido/não lido
- **Estatísticas**: Total de dispositivos, online, SMS recebidos

---

## 🏗️ Arquitetura

```
┌─────────────────────────────────────────┐
│     1.000 Dispositivos Android          │
│  (cada um com IP diferente)             │
└──────────────────┬──────────────────────┘
                   │
                   │ WebSocket + UDP Discovery
                   │
┌──────────────────▼──────────────────────┐
│         Servidor Node.js (PC)           │
│   - Express                             │
│   - WebSocket                           │
│   - SQLite Database                     │
│   - UDP Discovery                       │
└──────────────────┬──────────────────────┘
                   │
                   │ HTTP + WebSocket
                   │
┌──────────────────▼──────────────────────┐
│      Dashboard Web (Navegador)          │
│   - Lista de dispositivos               │
│   - SMS em tempo real                   │
│   - Filtros e busca                     │
└─────────────────────────────────────────┘
```

---

## 📱 Android App - Funcionamento

1. **Inicia automaticamente** ao ligar o telefone
2. **Descobre o servidor** via UDP broadcast
3. **Se registra** com seu número de telefone e informações
4. **Fica escutando** SMS
5. **Envia SMS para servidor** em tempo real via WebSocket
6. **Reconecta automaticamente** se a rede mudar

---

## 🗄️ Banco de Dados

### Tabela: `devices`
```sql
id              - Identificador único
phoneNumber     - Número de telefone
deviceName      - Modelo do dispositivo
ipAddress       - IP do dispositivo
status          - online/offline
lastSeen        - Último acesso
createdAt       - Data de criação
```

### Tabela: `messages`
```sql
id              - ID da mensagem
deviceId        - ID do dispositivo
phoneNumber     - Número do dispositivo
sender          - Remetente do SMS
body            - Conteúdo do SMS
timestamp       - Hora recebida
read            - Lido/não lido
```

---

## 🔌 API REST

### Dispositivos
- `GET /api/devices` - Lista todos os dispositivos
- `GET /api/devices/:phoneNumber` - Info de um dispositivo

### Mensagens
- `GET /api/messages` - Últimas 100 mensagens
- `GET /api/messages/:phoneNumber` - SMS de um número
- `PUT /api/messages/:id/read` - Marcar como lido
- `DELETE /api/messages/:id` - Deletar mensagem

### Estatísticas
- `GET /api/stats` - Stats gerais

---

## 🔍 Descoberta Automática de Servidor

O app usa **UDP broadcast** para encontrar o servidor automaticamente:

1. App envia pacote UDP: `DISCOVER_SMS_GATEWAY`
2. Servidor responde com: `ws://seu-ip:3000`
3. App se conecta

Isso significa que não precisa configurar IP manualmente! 🎯

---

## 🛡️ Segurança

### Recomendações:

1. **Use em rede privada** (mesma WiFi)
2. **Implemente autenticação** se em produção
3. **Use HTTPS/WSS** em produção
4. **Valide dados** do lado do servidor
5. **Limite acesso** ao dashboard

---

## 📝 Exemplo de Uso

### 1.000 Dispositivos:

```
Dispositivo 1 (IP: 192.168.1.50)
├─ Número: +55 11 99999-0001
├─ Status: Online
└─ SMS recebidos: 145

Dispositivo 2 (IP: 192.168.1.51)
├─ Número: +55 11 99999-0002
├─ Status: Online
└─ SMS recebidos: 238

Dispositivo 3 (IP: 192.168.1.52)
├─ Número: +55 11 99999-0003
├─ Status: Offline
└─ SMS recebidos: 89

...

Dispositivo 1000 (IP: 192.168.1.1049)
├─ Número: +55 11 99999-1000
├─ Status: Online
└─ SMS recebidos: 567

═══════════════════════════════════════════
Total: 1000 dispositivos
Online: 987
SMS Total: 245,632
═══════════════════════════════════════════
```

---

## 🐛 Troubleshooting

### App não se conecta
1. Verifique se servidor está rodando
2. Verifique se estão na mesma rede WiFi
3. Verifique firewall (porta 3000)

### SMS não aparece
1. Verifique permissões no Android
2. Verifique se app é SMS padrão
3. Verifique logs do servidor

### Dashboard não carrega
1. Acesse `http://seu-ip:3000`
2. Verifique se Node.js está rodando
3. Verifique console do navegador (F12)

---

## 📞 Suporte

Para dúvidas ou problemas, verifique:
- Logs do servidor: Terminal onde rodou `npm start`
- Logs do Android: Adb logcat
- Console do navegador: F12

---

## 📄 Licença

MIT

---

**Desenvolvido para gerenciar grandes volumes de SMS!** 🚀
