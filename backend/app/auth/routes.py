import hmac
import logging
from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel, Field
from typing import Optional
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from app.config import settings
from app.database import get_db
from app.models import User, Device
from app.auth.security import hash_password, verify_password, create_access_token, create_refresh_token, decode_token, get_current_user_id

logger = logging.getLogger("auth")
router = APIRouter(prefix="/api/v1/auth", tags=["auth"])

class RegisterRequest(BaseModel):
    username: str = Field(..., min_length=3, max_length=64)
    password: str = Field(..., min_length=6)
    email: Optional[str] = None
    phone: Optional[str] = None
    registration_id: int
    device_id: int = 1
    fcm_token: Optional[str] = None
    invite_code: Optional[str] = None

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
    email: Optional[str] = None
    phone: Optional[str] = None

class ProfileUpdateRequest(BaseModel):
    phone: str = Field(..., min_length=6, max_length=32)
    email: Optional[str] = None
    username: Optional[str] = None

class ProfileUpdateResponse(BaseModel):
    user_id: str
    username: str
    phone: Optional[str] = None
    email: Optional[str] = None
    access_token: Optional[str] = None
    refresh_token: Optional[str] = None

class UserProfileResponse(BaseModel):
    user_id: str
    username: str
    phone: Optional[str] = None
    email: Optional[str] = None

@router.post("/register", response_model=AuthResponse)
async def register(req: RegisterRequest, db: AsyncSession = Depends(get_db)):
    logger.info("Register attempt: username=%s, phone=%s, email=%s, is_guest=%s", req.username, req.phone, req.email, req.username.startswith("user_"))
    if not settings.ALLOW_REGISTRATION:
        raise HTTPException(status_code=403, detail="Registration closed")

    if settings.INVITE_CODE and not hmac.compare_digest(
        (req.invite_code or ""), settings.INVITE_CODE
    ):
        raise HTTPException(status_code=403, detail="Invalid invite code")

    stmt = select(User).where(User.username == req.username)
    res = await db.execute(stmt)
    if res.scalar_one_or_none():
        raise HTTPException(status_code=400, detail="Username already taken")

    phone_clean = None
    if req.phone and req.phone.strip():
        phone_clean = req.phone.strip().replace(" ", "").replace("-", "")
        stmt = select(User).where(User.phone == phone_clean)
        res = await db.execute(stmt)
        if res.scalar_one_or_none():
            raise HTTPException(status_code=400, detail="Phone number already registered")
    elif not req.username.startswith("user_"):
        raise HTTPException(status_code=400, detail="Phone number is mandatory")

    if req.email:
        email_clean = req.email.strip().lower()
        stmt = select(User).where(User.email == email_clean)
        res = await db.execute(stmt)
        if res.scalar_one_or_none():
            raise HTTPException(status_code=400, detail="Email already registered")

    user = User(
        username=req.username,
        email=req.email.strip().lower() if req.email else None,
        phone=phone_clean,
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

    logger.info("Register success: user_id=%s, username=%s", user.id, user.username)

    token_data = {"sub": str(user.id), "username": user.username, "device_id": req.device_id}
    return AuthResponse(
        user_id=str(user.id),
        username=user.username,
        device_id=req.device_id,
        access_token=create_access_token(token_data),
        refresh_token=create_refresh_token(token_data),
        email=user.email,
        phone=user.phone
    )

@router.post("/login", response_model=AuthResponse)
async def login(req: LoginRequest, db: AsyncSession = Depends(get_db)):
    logger.info("Login attempt: username=%s", req.username)
    stmt = select(User).where(User.username == req.username)
    res = await db.execute(stmt)
    user = res.scalar_one_or_none()
    if not user or not verify_password(req.password, user.password_hash):
        logger.warning("Login failed: username=%s invalid credentials", req.username)
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

    logger.info("Login success: user_id=%s, username=%s", user.id, user.username)

    token_data = {"sub": str(user.id), "username": user.username, "device_id": req.device_id}
    return AuthResponse(
        user_id=str(user.id),
        username=user.username,
        device_id=req.device_id,
        access_token=create_access_token(token_data),
        refresh_token=create_refresh_token(token_data),
        email=user.email,
        phone=user.phone
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

@router.get("/me", response_model=UserProfileResponse)
async def get_my_profile(
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db)
):
    stmt = select(User).where(User.id == user_id)
    res = await db.execute(stmt)
    user = res.scalar_one_or_none()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")
    return UserProfileResponse(
        user_id=str(user.id),
        username=user.username,
        phone=user.phone,
        email=user.email
    )

@router.put("/profile", response_model=ProfileUpdateResponse)
async def update_profile(
    req: ProfileUpdateRequest,
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db)
):
    logger.info("Profile update attempt: user_id=%s, username=%s, phone=%s, email=%s", user_id, req.username, req.phone, req.email)
    stmt = select(User).where(User.id == user_id)
    res = await db.execute(stmt)
    user = res.scalar_one_or_none()
    if not user:
        logger.warning("Profile update failed: user_id=%s not found", user_id)
        raise HTTPException(status_code=404, detail="User not found")

    phone_clean = req.phone.strip().replace(" ", "").replace("-", "")
    if len(phone_clean) < 6:
        raise HTTPException(status_code=400, detail="Invalid phone number")

    # Check phone uniqueness
    stmt = select(User).where(User.phone == phone_clean, User.id != user_id)
    res = await db.execute(stmt)
    if res.scalar_one_or_none():
        logger.warning("Profile update failed: phone=%s taken", phone_clean)
        raise HTTPException(status_code=400, detail="Phone number already registered to another account")

    # Check email uniqueness
    email_clean = None
    if req.email and req.email.strip():
        email_clean = req.email.strip().lower()
        stmt = select(User).where(User.email == email_clean, User.id != user_id)
        res = await db.execute(stmt)
        if res.scalar_one_or_none():
            logger.warning("Profile update failed: email=%s taken", email_clean)
            raise HTTPException(status_code=400, detail="Email already registered to another account")

    # Check username uniqueness if changed
    if req.username and req.username.strip():
        new_username = req.username.strip().lstrip("@").lower().replace(" ", "_")
        if len(new_username) < 3:
            raise HTTPException(status_code=400, detail="Username must be at least 3 characters")
        if new_username != user.username:
            stmt = select(User).where(User.username == new_username, User.id != user_id)
            res = await db.execute(stmt)
            if res.scalar_one_or_none():
                logger.warning("Profile update failed: username=%s taken", new_username)
                raise HTTPException(status_code=400, detail="Username already taken")
            user.username = new_username

    user.phone = phone_clean
    if email_clean is not None:
        user.email = email_clean

    await db.commit()
    logger.info("Profile update success: user_id=%s, username=%s, phone=%s, email=%s", user.id, user.username, user.phone, user.email)

    token_data = {"sub": str(user.id), "username": user.username, "device_id": 1}
    return ProfileUpdateResponse(
        user_id=str(user.id),
        username=user.username,
        phone=user.phone,
        email=user.email,
        access_token=create_access_token(token_data),
        refresh_token=create_refresh_token(token_data)
    )

class ContactLookupRequest(BaseModel):
    phone_numbers: list[str]

class RegisteredContact(BaseModel):
    phone: str
    username: str
    user_id: str

@router.post("/contacts/lookup", response_model=list[RegisteredContact])
async def lookup_contacts(req: ContactLookupRequest, db: AsyncSession = Depends(get_db)):
    clean_phones = [p.strip().replace(" ", "").replace("-", "").replace("(", "").replace(")", "") for p in req.phone_numbers if p.strip()]
    if not clean_phones:
        return []
    
    stmt = select(User).where(User.phone.in_(clean_phones))
    res = await db.execute(stmt)
    users = res.scalars().all()
    
    return [
        RegisteredContact(phone=u.phone or "", username=u.username, user_id=str(u.id))
        for u in users
    ]

@router.get("/user/{username}", response_model=RegisteredContact)
async def get_user_by_username(username: str, db: AsyncSession = Depends(get_db)):
    clean_username = username.lstrip("@").strip()
    stmt = select(User).where(User.username == clean_username)
    res = await db.execute(stmt)
    user = res.scalar_one_or_none()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")
    return RegisteredContact(phone=user.phone or "", username=user.username, user_id=str(user.id))

