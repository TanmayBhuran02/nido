import os
import uuid
from datetime import datetime, timedelta
from fastapi import APIRouter, Depends, HTTPException, UploadFile, File
from fastapi.responses import FileResponse
from pydantic import BaseModel
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from app.config import settings
from app.database import get_db
from app.models import Blob
from app.auth.security import get_current_user_id

router = APIRouter(prefix="/api/v1/media", tags=["media"])
os.makedirs(settings.LOCAL_STORAGE_DIR, exist_ok=True)

class RequestUpload(BaseModel):
    size_bytes: int
    sha256_hash: str
    mime_type: str = "application/octet-stream"

class UploadResponse(BaseModel):
    blob_id: str
    upload_url: str
    expires_in_seconds: int

@router.post("/upload-url", response_model=UploadResponse)
async def get_upload_url(
    req: RequestUpload,
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db)
):
    if req.size_bytes > 50 * 1024 * 1024:
        raise HTTPException(status_code=400, detail="File size exceeds 50MB limit")

    blob_id = str(uuid.uuid4())
    blob = Blob(
        id=blob_id,
        owner_id=user_id,
        size_bytes=req.size_bytes,
        sha256_hash=req.sha256_hash,
        mime_type=req.mime_type,
        expires_at=datetime.utcnow() + timedelta(days=30)
    )
    db.add(blob)
    await db.commit()

    return UploadResponse(
        blob_id=blob_id,
        upload_url=f"/api/v1/media/upload/{blob_id}",
        expires_in_seconds=3600
    )

@router.put("/upload/{blob_id}")
async def upload_direct_file(
    blob_id: str,
    file: UploadFile = File(...),
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db)
):
    stmt = select(Blob).where(Blob.id == blob_id)
    res = await db.execute(stmt)
    blob = res.scalar_one_or_none()
    if not blob:
        raise HTTPException(status_code=404, detail="Upload reservation not found")
    if blob.owner_id != user_id:
        raise HTTPException(status_code=403, detail="Not authorized to upload to this blob")
    if blob.expires_at < datetime.utcnow():
        raise HTTPException(status_code=410, detail="Upload reservation expired")

    target_path = os.path.join(settings.LOCAL_STORAGE_DIR, blob_id)
    temp_path = f"{target_path}.tmp.{uuid.uuid4().hex}"
    bytes_written = 0
    try:
        with open(temp_path, "wb") as f:
            while chunk := await file.read(64 * 1024):
                bytes_written += len(chunk)
                if bytes_written > blob.size_bytes:
                    raise HTTPException(status_code=413, detail="File exceeds reserved size")
                f.write(chunk)
        os.replace(temp_path, target_path)
    except Exception:
        if os.path.exists(temp_path):
            try:
                os.remove(temp_path)
            except OSError:
                pass
        raise
    return {"status": "uploaded", "blob_id": blob_id}

@router.get("/download-url/{blob_id}")
async def get_download_url(
    blob_id: str,
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db)
):
    stmt = select(Blob).where(Blob.id == blob_id)
    res = await db.execute(stmt)
    blob = res.scalar_one_or_none()
    if not blob:
        raise HTTPException(status_code=404, detail="Blob not found")

    return {
        "blob_id": blob_id,
        "download_url": f"/api/v1/media/file/{blob_id}",
        "size_bytes": blob.size_bytes,
        "sha256_hash": blob.sha256_hash,
        "mime_type": blob.mime_type
    }

@router.get("/file/{blob_id}")
async def serve_file(blob_id: str):
    target_path = os.path.join(settings.LOCAL_STORAGE_DIR, blob_id)
    if not os.path.exists(target_path):
        raise HTTPException(status_code=404, detail="File not found on disk")
    return FileResponse(target_path, media_type="application/octet-stream")
