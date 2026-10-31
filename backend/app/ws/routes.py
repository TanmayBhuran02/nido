import json
import logging
from typing import Dict, Optional
from datetime import datetime
from fastapi import APIRouter, WebSocket, WebSocketDisconnect, Query
from app.auth.security import decode_token

router = APIRouter(tags=["websocket"])

class ConnectionManager:
    def __init__(self):
        # Maps user_id -> dict of {device_id: WebSocket}
        self.active_connections: Dict[str, Dict[int, WebSocket]] = {}
        # Maps user_id -> last_seen timestamp ISO string
        self.last_seen: Dict[str, str] = {}
        # Maps user_id -> bool (privacy toggle: show last seen)
        self.privacy_last_seen: Dict[str, bool] = {}

    async def connect(self, user_id: str, device_id: int, websocket: WebSocket):
        await websocket.accept()
        if user_id not in self.active_connections:
            self.active_connections[user_id] = {}
        self.active_connections[user_id][device_id] = websocket
        self.last_seen[user_id] = datetime.utcnow().isoformat()

    def disconnect(self, user_id: str, device_id: int, websocket: WebSocket):
        if user_id in self.active_connections:
            if self.active_connections[user_id].get(device_id) == websocket:
                self.active_connections[user_id].pop(device_id, None)
                if not self.active_connections[user_id]:
                    self.active_connections.pop(user_id, None)
        self.last_seen[user_id] = datetime.utcnow().isoformat()

    async def send_to_device(self, user_id: str, device_id: int, data: dict) -> bool:
        user_devices = self.active_connections.get(user_id)
        if user_devices and device_id in user_devices:
            try:
                await user_devices[device_id].send_text(json.dumps(data))
                return True
            except Exception:
                return False
        return False

    async def broadcast_to_user(self, user_id: str, data: dict):
        user_devices = self.active_connections.get(user_id, {})
        for dev_ws in list(user_devices.values()):
            try:
                await dev_ws.send_text(json.dumps(data))
            except Exception:
                pass

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

                if event_type == "ping":
                    await websocket.send_text(json.dumps({"type": "pong"}))

                elif event_type == "typing":
                    # Typing event: { type: "typing", recipient_user_id: str, is_typing: bool, group_id?: str }
                    recipient_id = event.get("recipient_user_id")
                    typing_envelope = {
                        "type": "typing",
                        "sender_user_id": user_id,
                        "is_typing": event.get("is_typing", True),
                        "group_id": event.get("group_id")
                    }
                    if recipient_id:
                        await manager.broadcast_to_user(recipient_id, typing_envelope)

                elif event_type == "receipt":
                    # Read receipt or delivery receipt
                    recipient_id = event.get("recipient_user_id")
                    recipient_dev = event.get("recipient_device_id", 1)
                    receipt_envelope = {
                        "type": "receipt",
                        "sender_user_id": user_id,
                        "sender_device_id": device_id,
                        "message_id": event.get("message_id"),
                        "status": event.get("status")  # 'delivered', 'read'
                    }
                    if recipient_id:
                        await manager.send_to_device(recipient_id, recipient_dev, receipt_envelope)

                elif event_type == "privacy_update":
                    manager.privacy_last_seen[user_id] = event.get("show_last_seen", True)

                elif event_type == "presence_query":
                    target_user = event.get("target_user_id")
                    if target_user:
                        can_see = manager.privacy_last_seen.get(target_user, True)
                        is_online = (target_user in manager.active_connections) if can_see else False
                        last_seen_val = manager.last_seen.get(target_user) if can_see else None
                        await websocket.send_text(json.dumps({
                            "type": "presence_result",
                            "target_user_id": target_user,
                            "online": is_online,
                            "last_seen": last_seen_val
                        }))

            except Exception as e:
                logging.error(f"WS message handling error: {e}")
    finally:
        manager.disconnect(user_id, device_id, websocket)
