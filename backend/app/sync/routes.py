from typing import List
from fastapi import APIRouter, Depends
from pydantic import BaseModel
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from app.database import get_db
from app.models import SyncedTrack
from app.auth.security import get_current_user_id

router = APIRouter(prefix="/api/v1/sync", tags=["sync"])

class SyncedTrackIn(BaseModel):
    track_id: str
    title: str
    artist: str
    duration_ms: int

class SyncedTrackOut(BaseModel):
    id: str
    track_id: str
    title: str
    artist: str
    duration_ms: int
    downloaded_at: str

@router.post("/library", response_model=SyncedTrackOut)
async def sync_downloaded_track(
    track: SyncedTrackIn,
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db)
):
    stmt = select(SyncedTrack).where(SyncedTrack.user_id == user_id, SyncedTrack.track_id == track.track_id)
    res = await db.execute(stmt)
    existing = res.scalar_one_or_none()

    if existing:
        return SyncedTrackOut(
            id=existing.id,
            track_id=existing.track_id,
            title=existing.title,
            artist=existing.artist,
            duration_ms=existing.duration_ms,
            downloaded_at=existing.downloaded_at.isoformat()
        )

    record = SyncedTrack(
        user_id=user_id,
        track_id=track.track_id,
        title=track.title,
        artist=track.artist,
        duration_ms=track.duration_ms
    )
    db.add(record)
    await db.commit()

    return SyncedTrackOut(
        id=record.id,
        track_id=record.track_id,
        title=record.title,
        artist=record.artist,
        duration_ms=record.duration_ms,
        downloaded_at=record.downloaded_at.isoformat()
    )

@router.get("/library", response_model=List[SyncedTrackOut])
async def get_synced_library(
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db)
):
    stmt = select(SyncedTrack).where(SyncedTrack.user_id == user_id).order_by(SyncedTrack.downloaded_at.desc())
    res = await db.execute(stmt)
    rows = res.scalars().all()

    return [
        SyncedTrackOut(
            id=r.id,
            track_id=r.track_id,
            title=r.title,
            artist=r.artist,
            duration_ms=r.duration_ms,
            downloaded_at=r.downloaded_at.isoformat()
        )
        for r in rows
    ]
