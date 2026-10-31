from typing import List
from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, delete
from app.database import get_db
from app.models import PendingMessage
from app.auth.security import get_current_user_id

router = APIRouter(prefix="/api/v1/messages", tags=["messages"])

class SendMessageRequest(BaseModel):
    recipient_user_id: str
    recipient_device_id: int = 1
    ciphertext: str
    message_type: int = 1
    sender_device_id: int = 1

class PendingMessageOut(BaseModel):
    id: str
    sender_user_id: str
    sender_device_id: int
    ciphertext: str
    message_type: int
    created_at: str

@router.post("/send")
async def send_message(
    req: SendMessageRequest,
    sender_user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db)
):
    msg = PendingMessage(
        recipient_user_id=req.recipient_user_id,
        recipient_device_id=req.recipient_device_id,
        sender_user_id=sender_user_id,
        sender_device_id=req.sender_device_id,
        ciphertext=req.ciphertext,
        message_type=req.message_type
    )
    db.add(msg)
    await db.commit()
    return {"status": "queued", "message_id": msg.id}

@router.get("/pending", response_model=List[PendingMessageOut])
async def get_pending_messages(
    device_id: int = 1,
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db)
):
    stmt = (
        select(PendingMessage)
        .where(PendingMessage.recipient_user_id == user_id, PendingMessage.recipient_device_id == device_id)
        .order_by(PendingMessage.created_at.asc())
    )
    res = await db.execute(stmt)
    rows = res.scalars().all()

    return [
        PendingMessageOut(
            id=r.id,
            sender_user_id=r.sender_user_id,
            sender_device_id=r.sender_device_id,
            ciphertext=r.ciphertext,
            message_type=r.message_type,
            created_at=r.created_at.isoformat()
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
