from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel, Field
from typing import Optional
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from app.database import get_db
from app.models import User, Device
from app.auth.security import hash_password, verify_password, create_access_token, create_refresh_token, decode_token

router = APIRouter(prefix="/api/v1/auth", tags=["auth"])

class RegisterRequest(BaseModel):
    username: str = Field(..., min_length=3, max_length=64)
    password: str = Field(..., min_length=6)
    phone: Optional[str] = None
    registration_id: int
    device_id: int = 1
    fcm_token: Optional[str] = None

class LoginRequest(BaseModel):
    username: str
    password: str
    device_id: int = 1
    fcm_token: Optional[str] = None

class RefreshRequest(BaseModel):
    refresh_token: str

class AuthResponse(BaseModel):
    user_id: str
    username: str
    device_id: int
    access_token: str
    refresh_token: str
    token_type: str = "bearer"

@router.post("/register", response_model=AuthResponse)
async def register(req: RegisterRequest, db: AsyncSession = Depends(get_db)):
    stmt = select(User).where(User.username == req.username)
    res = await db.execute(stmt)
    if res.scalar_one_or_none():
        raise HTTPException(status_code=400, detail="Username already taken")

    user = User(
        username=req.username,
        phone=req.phone,
        password_hash=hash_password(req.password)
    )
    db.add(user)
    await db.flush()

    device = Device(
        user_id=user.id,
        device_id=req.device_id,
        registration_id=req.registration_id,
        fcm_token=req.fcm_token
    )
    db.add(device)
    await db.commit()

    token_data = {"sub": str(user.id), "username": user.username, "device_id": req.device_id}
    return AuthResponse(
        user_id=str(user.id),
        username=user.username,
        device_id=req.device_id,
        access_token=create_access_token(token_data),
        refresh_token=create_refresh_token(token_data)
    )

@router.post("/login", response_model=AuthResponse)
async def login(req: LoginRequest, db: AsyncSession = Depends(get_db)):
    stmt = select(User).where(User.username == req.username)
    res = await db.execute(stmt)
    user = res.scalar_one_or_none()
    if not user or not verify_password(req.password, user.password_hash):
        raise HTTPException(status_code=401, detail="Invalid username or password")

    # Update or add device
    stmt = select(Device).where(Device.user_id == user.id, Device.device_id == req.device_id)
    dev_res = await db.execute(stmt)
    device = dev_res.scalar_one_or_none()
    if not device:
        raise HTTPException(status_code=404, detail="Device not found")
    if req.fcm_token:
        device.fcm_token = req.fcm_token
        await db.commit()

    token_data = {"sub": str(user.id), "username": user.username, "device_id": req.device_id}
    return AuthResponse(
        user_id=str(user.id),
        username=user.username,
        device_id=req.device_id,
        access_token=create_access_token(token_data),
        refresh_token=create_refresh_token(token_data)
    )

@router.post("/refresh")
async def refresh(req: RefreshRequest):
    payload = decode_token(req.refresh_token)
    if payload.get("type") != "refresh":
        raise HTTPException(status_code=401, detail="Invalid token type")
    
    user_id = payload.get("sub")
    username = payload.get("username")
    device_id = payload.get("device_id", 1)

    new_token_data = {"sub": user_id, "username": username, "device_id": device_id}
    return {
        "access_token": create_access_token(new_token_data),
        "refresh_token": create_refresh_token(new_token_data),
        "token_type": "bearer"
    }
