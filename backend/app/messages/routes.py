import uuid
from typing import List, Optional
from datetime import datetime, timedelta
from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, delete, update
from app.database import get_db
from app.models import PendingMessage, MessageReceipt, User
from app.auth.security import get_current_user_id
from app.ws.routes import manager

router = APIRouter(prefix="/api/v1/messages", tags=["messages"])

class SendMessageRequest(BaseModel):
    recipient_user_id: str
    recipient_device_id: int = 1
    ciphertext: str = Field(..., max_length=65536)
    message_type: int = 1
    sender_device_id: int = 1
    reply_to_id: Optional[str] = None
    is_forwarded: bool = False
    client_msg_id: Optional[str] = None

class EditMessageRequest(BaseModel):
    ciphertext: str = Field(..., max_length=65536)

class PendingMessageOut(BaseModel):
    id: str
    sender_user_id: str
    sender_username: Optional[str] = None
    sender_device_id: int
    ciphertext: str
    message_type: int
    group_id: Optional[str] = None
    reply_to_id: Optional[str] = None
    is_deleted: bool = False
    is_forwarded: bool = False
    created_at: str

@router.post("/send")
async def send_message(
    req: SendMessageRequest,
    sender_user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db)
):
    recipient_stmt = select(User).where(User.id == req.recipient_user_id)
    recipient_res = await db.execute(recipient_stmt)
    if not recipient_res.scalar_one_or_none():
        raise HTTPException(status_code=404, detail="Recipient user not found")

    now = datetime.utcnow()
    msg_id = req.client_msg_id.strip() if req.client_msg_id and req.client_msg_id.strip() else str(uuid.uuid4())
    msg = PendingMessage(
        id=msg_id,
        recipient_user_id=req.recipient_user_id,
        recipient_device_id=req.recipient_device_id,
        sender_user_id=sender_user_id,
        sender_device_id=req.sender_device_id,
        ciphertext=req.ciphertext,
        message_type=req.message_type,
        reply_to_id=req.reply_to_id,
        is_forwarded=req.is_forwarded,
        created_at=now
    )
    db.add(msg)
    await db.commit()
    await db.refresh(msg)

    # Attempt direct WebSocket dispatch
    envelope = {
        "type": "message",
        "id": msg.id,
        "sender_user_id": sender_user_id,
        "sender_device_id": req.sender_device_id,
        "ciphertext": req.ciphertext,
        "message_type": req.message_type,
        "reply_to_id": req.reply_to_id,
        "is_forwarded": req.is_forwarded,
        "created_at": now.isoformat()
    }
    await manager.send_to_device(req.recipient_user_id, req.recipient_device_id, envelope)

    return {"status": "queued", "message_id": msg.id}

@router.get("/pending", response_model=List[PendingMessageOut])
async def get_pending_messages(
    device_id: int = 1,
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db)
):
    stmt = (
        select(PendingMessage, User.username)
        .join(User, User.id == PendingMessage.sender_user_id, isouter=True)
        .where(PendingMessage.recipient_user_id == user_id, PendingMessage.recipient_device_id == device_id)
        .order_by(PendingMessage.created_at.asc())
    )
    res = await db.execute(stmt)
    rows = res.all()

    # Automatically record DELIVERED receipts for sender
    for r in rows:
        pm = r[0]
        rcpt = MessageReceipt(
            message_id=pm.id,
            recipient_user_id=pm.sender_user_id,
            status="DELIVERED"
        )
        db.add(rcpt)
    if rows:
        await db.commit()

    return [
        PendingMessageOut(
            id=r[0].id,
            sender_user_id=r[0].sender_user_id,
            sender_username=r[1],
            sender_device_id=r[0].sender_device_id,
            ciphertext=r[0].ciphertext,
            message_type=r[0].message_type,
            group_id=r[0].group_id,
            reply_to_id=r[0].reply_to_id,
            is_deleted=r[0].is_deleted,
            is_forwarded=r[0].is_forwarded,
            created_at=r[0].created_at.isoformat()
        )
        for r in rows
    ]

@router.post("/ack/{message_id}")
async def ack_message(
    message_id: str,
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db)
):
    stmt = delete(PendingMessage).where(
        PendingMessage.id == message_id,
        PendingMessage.recipient_user_id == user_id
    )
    await db.execute(stmt)
    await db.commit()
    return {"status": "acknowledged"}

class MarkReadRequest(BaseModel):
    message_ids: List[str]
    peer_user_id: Optional[str] = None

class ReceiptOut(BaseModel):
    message_id: str
    status: str

@router.post("/receipts/read")
async def send_read_receipts(
    req: MarkReadRequest,
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db)
):
    if not req.message_ids:
        return {"status": "ok"}
    for mid in req.message_ids:
        target_recipient = req.peer_user_id
        if not target_recipient:
            stmt = select(MessageReceipt.recipient_user_id).where(MessageReceipt.message_id == mid)
            res = await db.execute(stmt)
            target_recipient = res.scalar_one_or_none()
        if target_recipient:
            rcpt = MessageReceipt(
                message_id=mid,
                recipient_user_id=target_recipient,
                status="READ"
            )
            db.add(rcpt)
    await db.commit()
    return {"status": "read_receipts_recorded"}

@router.get("/receipts/pending", response_model=List[ReceiptOut])
async def get_pending_receipts(
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db)
):
    stmt = select(MessageReceipt).where(MessageReceipt.recipient_user_id == user_id)
    res = await db.execute(stmt)
    receipts = res.scalars().all()
    return [ReceiptOut(message_id=r.message_id, status=r.status) for r in receipts]

class AckReceiptsRequest(BaseModel):
    message_ids: List[str]

@router.post("/receipts/ack")
async def ack_receipts(
    req: AckReceiptsRequest,
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db)
):
    if req.message_ids:
        stmt = delete(MessageReceipt).where(
            MessageReceipt.recipient_user_id == user_id,
            MessageReceipt.message_id.in_(req.message_ids)
        )
        await db.execute(stmt)
        await db.commit()
    return {"status": "receipts_acknowledged"}

@router.patch("/{message_id}")
async def edit_message(
    message_id: str,
    req: EditMessageRequest,
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db)
):
    # Check pending message ownership
    stmt = select(PendingMessage).where(PendingMessage.id == message_id, PendingMessage.sender_user_id == user_id)
    res = await db.execute(stmt)
    msg = res.scalar_one_or_none()
    if not msg or msg.is_deleted:
        raise HTTPException(status_code=404, detail="Message not found or deleted")

    # Time-limited: allow edit within 15 minutes of creation
    if datetime.utcnow() - msg.created_at > timedelta(minutes=15):
        raise HTTPException(status_code=400, detail="Edit time window expired")

    msg.ciphertext = req.ciphertext
    await db.commit()

    # Broadcast edit event via WS
    await manager.send_to_device(msg.recipient_user_id, msg.recipient_device_id, {
        "type": "message_edited",
        "message_id": message_id,
        "ciphertext": req.ciphertext
    })
    return {"status": "edited"}

@router.delete("/{message_id}")
async def delete_for_everyone(
    message_id: str,
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db)
):
    stmt = select(PendingMessage).where(PendingMessage.id == message_id, PendingMessage.sender_user_id == user_id)
    res = await db.execute(stmt)
    msg = res.scalar_one_or_none()
    if not msg:
        raise HTTPException(status_code=404, detail="Message not found")

    msg.is_deleted = True
    msg.ciphertext = ""
    await db.commit()
    await manager.send_to_device(msg.recipient_user_id, msg.recipient_device_id, {
        "type": "message_deleted",
        "message_id": message_id
    })
    return {"status": "deleted_for_everyone"}
