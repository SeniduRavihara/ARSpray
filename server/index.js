// server/index.js
const { WebSocketServer } = require('ws');

const port = process.env.PORT || 8080;
const wss = new WebSocketServer({ port });

// In-memory store: roomId -> { clients: Set, history: Array }
const rooms = new Map();

console.log(`AR Sync WebSocket Server running on port ${port}`);

wss.on('connection', (ws) => {
    let currentRoomId = null;

    ws.on('message', (messageText) => {
        try {
            const data = JSON.parse(messageText.toString());
            const { action, roomId } = data;

            if (!roomId) return;

            // Get or create room state
            if (!rooms.has(roomId)) {
                rooms.set(roomId, { clients: new Set(), history: [] });
            }
            const room = rooms.get(roomId);

            switch (action) {
                case 'join':
                    currentRoomId = roomId;
                    room.clients.add(ws);
                    console.log(`Client joined room: ${roomId}. Total clients: ${room.clients.size}`);
                    
                    // Send room history to the newly joined client
                    ws.send(JSON.stringify({
                        action: 'history',
                        nodes: room.history
                    }));
                    break;

                case 'add_node':
                    if (data.node) {
                        room.history.push(data.node);
                        // Broadcast node to other clients in the room
                        const broadcastMsg = JSON.stringify({
                            action: 'add_node',
                            node: data.node
                        });
                        room.clients.forEach((client) => {
                            if (client !== ws && client.readyState === ws.OPEN) {
                                client.send(broadcastMsg);
                            }
                        });
                    }
                    break;

                case 'clear':
                    room.history = [];
                    // Broadcast clear to all clients in the room
                    const clearMsg = JSON.stringify({ action: 'clear' });
                    room.clients.forEach((client) => {
                        if (client.readyState === ws.OPEN) {
                            client.send(clearMsg);
                        }
                    });
                    console.log(`Cleared room: ${roomId}`);
                    break;

                default:
                    console.warn(`Unknown action: ${action}`);
            }
        } catch (err) {
            console.error('Error handling message:', err);
        }
    });

    ws.on('close', () => {
        if (currentRoomId && rooms.has(currentRoomId)) {
            const room = rooms.get(currentRoomId);
            room.clients.delete(ws);
            console.log(`Client disconnected from room: ${currentRoomId}. Remaining clients: ${room.clients.size}`);
            
            // Cleanup empty room
            if (room.clients.size === 0) {
                rooms.delete(currentRoomId);
                console.log(`Room ${currentRoomId} deleted (no active clients).`);
            }
        }
    });
});
