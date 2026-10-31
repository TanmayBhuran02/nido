import json
import logging
from typing import Dict
from fastapi import APIRouter, WebSocket, WebSocketDisconnect, Query
from app.auth.security import decode_token

router = APIRouter(tags=["websocket"])

class ConnectionManager:
    def __init__(self):
        # Maps user_id -> dict of {device_id: WebSocket}
        self.active_connections: Dict[str, Dict[int, WebSocket]] = {}

    async def connect(self, user_id: str, device_id: int, websocket: WebSocket):
        await websocket.accept()
        if user_id not in self.active_connections:
            self.active_connections[user_id] = {}
        self.active_connections[user_id][device_id] = websocket

    def disconnect(self, user_id: str, device_id: int, websocket: WebSocket):
        if user_id in self.active_connections:
            if self.active_connections[user_id].get(device_id) == websocket:
                self.active_connections[user_id].pop(device_id, None)
                if not self.active_connections[user_id]:
                    self.active_connections.pop(user_id, None)

    async def send_to_device(self, user_id: str, device_id: int, data: dict) -> bool:
        user_devices = self.active_connections.get(user_id)
        if user_devices and device_id in user_devices:
            await user_devices[device_id].send_text(json.dumps(data))
            return True
        return False

manager = ConnectionManager()

@router.websocket("/api/v1/ws")
async def websocket_endpoint(websocket: WebSocket, token: str = Query(...)):
    try:
        payload = decode_token(token)
        user_id = payload.get("sub")
        device_id = payload.get("device_id", 1)
        if not user_id:
            await websocket.close(code=4001)
            return
    except Exception:
        await websocket.close(code=4001)
        return

    await manager.connect(user_id, device_id, websocket)
    try:
        while True:
            raw_text = await websocket.receive_text()
            try:
                event = json.loads(raw_text)
                event_type = event.get("type")
                # e.g., 'ping' -> 'pong', 'receipt' -> forward receipt
                if event_type == "ping":
                    await websocket.send_text(json.dumps({"type": "pong"}))
                elif event_type == "receipt":
                    recipient_id = event.get("recipient_user_id")
                    recipient_dev = event.get("recipient_device_id", 1)
                    receipt_envelope = {
                        "type": "receipt",
                        "sender_user_id": user_id,
                        "sender_device_id": device_id,
                        "message_id": event.get("message_id"),
                        "status": event.get("status")
                    }
                    await manager.send_to_device(recipient_id, recipient_dev, receipt_envelope)
            except Exception as e:
                logging.error(f"WS message handling error: {e}")
    finally:
        manager.disconnect(user_id, device_id, websocket)
