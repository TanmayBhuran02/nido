import json
import uuid
from typing import List, Optional
from datetime import datetime
from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from app.database import get_db
from app.models import StickerPack
from app.auth.security import get_current_user_id

router = APIRouter(prefix="/api/v1/stickers", tags=["stickers"])

class StickerItem(BaseModel):
    image_file: str
    emojis: List[str] = []

class CreateStickerPackRequest(BaseModel):
    identifier: str = Field(..., max_length=64)
    name: str = Field(..., max_length=128)
    publisher: str = "Nido"
    tray_image_file: Optional[str] = None
    animated: bool = False
    stickers: List[StickerItem]

class StickerPackOut(BaseModel):
    id: str
    name: str
    creator: str
    animated: bool
    stickers: List[dict]
    created_at: str

@router.get("/packs", response_model=List[StickerPackOut])
async def list_sticker_packs(
    db: AsyncSession = Depends(get_db)
):
    stmt = select(StickerPack).order_by(StickerPack.created_at.desc())
    res = await db.execute(stmt)
    packs = res.scalars().all()
    
    results = []
    for p in packs:
        try:
            stickers_list = json.loads(p.stickers_json)
        except Exception:
            stickers_list = []
        results.append(
            StickerPackOut(
                id=p.id,
                name=p.name,
                creator=p.creator,
                animated=p.animated,
                stickers=stickers_list,
                created_at=p.created_at.isoformat()
            )
        )
    return results

@router.post("/packs", status_code=status.HTTP_201_CREATED)
async def create_sticker_pack(
    req: CreateStickerPackRequest,
    current_user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db)
):
    # Check if pack already exists
    stmt = select(StickerPack).where(StickerPack.id == req.identifier)
    res = await db.execute(stmt)
    existing = res.scalar_one_or_none()
    
    stickers_data = [s.model_dump() for s in req.stickers]
    stickers_json_str = json.dumps(stickers_data)

    if existing:
        existing.name = req.name
        existing.creator = req.publisher
        existing.animated = req.animated
        existing.stickers_json = stickers_json_str
    else:
        pack = StickerPack(
            id=req.identifier,
            name=req.name,
            creator=req.publisher,
            animated=req.animated,
            stickers_json=stickers_json_str
        )
        db.add(pack)

    await db.commit()
    return {"status": "saved", "identifier": req.identifier}

@router.get("/packs/{pack_id}", response_model=StickerPackOut)
async def get_sticker_pack(
    pack_id: str,
    db: AsyncSession = Depends(get_db)
):
    stmt = select(StickerPack).where(StickerPack.id == pack_id)
    res = await db.execute(stmt)
    pack = res.scalar_one_or_none()
    if not pack:
        raise HTTPException(status_code=404, detail="Sticker pack not found")

    try:
        stickers_list = json.loads(pack.stickers_json)
    except Exception:
        stickers_list = []

    return StickerPackOut(
        id=pack.id,
        name=pack.name,
        creator=pack.creator,
        animated=pack.animated,
        stickers=stickers_list,
        created_at=pack.created_at.isoformat()
    )
