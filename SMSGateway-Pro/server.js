const express = require('express');
const http = require('http');
const WebSocket = require('ws');
const sqlite3 = require('sqlite3').verbose();
const cors = require('cors');
const path = require('path');
const dgram = require('dgram');
const os = require('os');

const app = express();
const server = http.createServer(app);
const wss = new WebSocket.Server({ server });

// Database setup
const db = new sqlite3.Database('./messages.db');

// Initialize database
db.run(`
    CREATE TABLE IF NOT EXISTS devices (
        id TEXT PRIMARY KEY,
        phoneNumber TEXT,
        deviceName TEXT,
        ipAddress TEXT,
        status TEXT,
        lastSeen INTEGER,
        createdAt INTEGER
    )
`);

db.run(`
    CREATE TABLE IF NOT EXISTS messages (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        deviceId TEXT,
        phoneNumber TEXT,
        sender TEXT,
        body TEXT,
        timestamp INTEGER,
        read INTEGER DEFAULT 0,
        FOREIGN KEY(deviceId) REFERENCES devices(id)
    )
`);

app.use(cors());
app.use(express.json());
app.use(express.static('public'));

// Store connected devices
const connectedDevices = new Map();

// WebSocket connection handler
wss.on('connection', (ws, req) => {
    console.log(`[WebSocket] Nova conexão de ${req.socket.remoteAddress}`);
    
    ws.on('message', (message) => {
        try {
            const data = JSON.parse(message);
            console.log(`[Mensagem] ${data.action} de ${data.deviceId}`);
            
            if (data.action === 'register') {
                handleDeviceRegistration(ws, data, req.socket.remoteAddress);
            } else if (data.action === 'sms') {
                handleSMSMessage(data);
            }
        } catch (e) {
            console.error('Erro ao processar mensagem:', e);
        }
    });
    
    ws.on('close', () => {
        console.log('[WebSocket] Conexão fechada');
        // Remover dispositivo
        for (let [deviceId, device] of connectedDevices) {
            if (device.ws === ws) {
                device.status = 'offline';
                device.lastSeen = Date.now();
                connectedDevices.delete(deviceId);
                broadcastDeviceUpdate();
                break;
            }
        }
    });
    
    ws.on('error', (err) => {
        console.error('[WebSocket Error]', err);
    });
});

function handleDeviceRegistration(ws, data, ipAddress) {
    const deviceId = data.deviceId;
    const phoneNumber = data.phoneNumber;
    const deviceName = data.deviceName;
    
    console.log(`[Registro] Dispositivo: ${deviceName} | Telefone: ${phoneNumber} | IP: ${ipAddress}`);
    
    // Armazenar no mapa
    connectedDevices.set(deviceId, {
        ws,
        phoneNumber,
        deviceName,
        ipAddress: ipAddress.replace('::ffff:', ''),
        status: 'online',
        lastSeen: Date.now()
    });
    
    // Salvar no banco de dados
    db.run(
        `INSERT OR REPLACE INTO devices (id, phoneNumber, deviceName, ipAddress, status, lastSeen, createdAt)
         VALUES (?, ?, ?, ?, 'online', ?, ?)`,
        [deviceId, phoneNumber, deviceName, ipAddress, Date.now(), Date.now()]
    );
    
    // Notificar clients web
    broadcastDeviceUpdate();
    
    console.log(`[Registro] Total de dispositivos conectados: ${connectedDevices.size}`);
}

function handleSMSMessage(data) {
    const { deviceId, phoneNumber, sender, body, timestamp } = data;
    
    console.log(`[SMS] De: ${sender} | Para: ${phoneNumber} | Msg: ${body.substring(0, 50)}...`);
    
    // Salvar no banco de dados
    db.run(
        `INSERT INTO messages (deviceId, phoneNumber, sender, body, timestamp)
         VALUES (?, ?, ?, ?, ?)`,
        [deviceId, phoneNumber, sender, body, timestamp]
    );
    
    // Notificar clients web
    broadcastSMSMessage({
        deviceId,
        phoneNumber,
        sender,
        body,
        timestamp,
        read: 0
    });
}

function broadcastDeviceUpdate() {
    const devices = Array.from(connectedDevices.values()).map(device => ({
        phoneNumber: device.phoneNumber,
        deviceName: device.deviceName,
        ipAddress: device.ipAddress,
        status: device.status,
        lastSeen: device.lastSeen
    }));
    
    broadcastToClients({
        type: 'device-list',
        devices,
        totalDevices: connectedDevices.size
    });
}

function broadcastSMSMessage(sms) {
    broadcastToClients({
        type: 'sms',
        sms
    });
}

function broadcastToClients(data) {
    const message = JSON.stringify(data);
    
    wss.clients.forEach(client => {
        if (client.readyState === WebSocket.OPEN) {
            client.send(message);
        }
    });
}

// REST API Routes
app.get('/api/devices', (req, res) => {
    const devices = Array.from(connectedDevices.values()).map(device => ({
        phoneNumber: device.phoneNumber,
        deviceName: device.deviceName,
        ipAddress: device.ipAddress,
        status: device.status,
        lastSeen: device.lastSeen
    }));
    
    res.json({
        totalDevices: connectedDevices.size,
        devices
    });
});

app.get('/api/messages', (req, res) => {
    db.all(
        `SELECT * FROM messages ORDER BY timestamp DESC LIMIT 100`,
        (err, rows) => {
            if (err) {
                res.status(500).json({ error: err.message });
                return;
            }
            res.json({ messages: rows });
        }
    );
});

app.get('/api/messages/:phoneNumber', (req, res) => {
    const { phoneNumber } = req.params;
    
    db.all(
        `SELECT * FROM messages WHERE phoneNumber = ? ORDER BY timestamp DESC`,
        [phoneNumber],
        (err, rows) => {
            if (err) {
                res.status(500).json({ error: err.message });
                return;
            }
            res.json({ messages: rows });
        }
    );
});

app.put('/api/messages/:id/read', (req, res) => {
    const { id } = req.params;
    
    db.run(
        `UPDATE messages SET read = 1 WHERE id = ?`,
        [id],
        (err) => {
            if (err) {
                res.status(500).json({ error: err.message });
                return;
            }
            res.json({ success: true });
        }
    );
});

app.get('/api/stats', (req, res) => {
    db.get(
        `SELECT COUNT(*) as totalMessages FROM messages`,
        (err, row) => {
            if (err) {
                res.status(500).json({ error: err.message });
                return;
            }
            
            res.json({
                totalDevices: connectedDevices.size,
                totalMessages: row.totalMessages,
                onlineDevices: Array.from(connectedDevices.values())
                    .filter(d => d.status === 'online').length
            });
        }
    );
});

// UDP Discovery Server
const discoveryServer = dgram.createSocket('udp4');

discoveryServer.on('message', (msg, rinfo) => {
    const message = msg.toString();
    
    if (message === 'DISCOVER_SMS_GATEWAY') {
        const localIp = getLocalIpAddress();
        const response = `ws://${localIp}:3000`;
        
        console.log(`[Discovery] Resposta enviada para ${rinfo.address}: ${response}`);
        
        discoveryServer.sendto(
            Buffer.from(response),
            0,
            response.length,
            rinfo.port,
            rinfo.address
        );
    }
});

discoveryServer.on('error', (err) => {
    console.error('Discovery error:', err);
});

function getLocalIpAddress() {
    const interfaces = os.networkInterfaces();
    
    for (const name of Object.keys(interfaces)) {
        for (const iface of interfaces[name]) {
            if (iface.family === 'IPv4' && !iface.internal) {
                return iface.address;
            }
        }
    }
    
    return 'localhost';
}

discoveryServer.bind(3000, () => {
    console.log('[Discovery] UDP server escutando na porta 3000');
});

// Serve frontend
app.get('/', (req, res) => {
    res.sendFile(path.join(__dirname, 'public/index.html'));
});

// Server start
const PORT = process.env.PORT || 3000;
server.listen(PORT, '0.0.0.0', () => {
    console.log(`
╔═══════════════════════════════════════════════════════════╗
║              SMS GATEWAY PRO - SERVIDOR                   ║
╚═══════════════════════════════════════════════════════════╝

🚀 Servidor rodando em: http://localhost:${PORT}
📱 WebSocket: ws://localhost:${PORT}
🔍 Discovery UDP: porta 3000

✅ Pronto para receber dispositivos!

    `);
});

// Handle graceful shutdown
process.on('SIGINT', () => {
    console.log('\n[Shutdown] Fechando conexões...');
    discoveryServer.close();
    db.close();
    server.close(() => {
        console.log('[Shutdown] Servidor encerrado');
        process.exit(0);
    });
});
