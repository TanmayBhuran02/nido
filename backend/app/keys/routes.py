from typing import List, Optional
from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, update, func
from app.database import get_db
from app.models import Device, IdentityKey, SignedPreKey, OneTimePreKey
from app.auth.security import get_current_user_id

router = APIRouter(prefix="/api/v1/keys", tags=["keys"])

class SignedPreKeyIn(BaseModel):
    key_id: int
    public_key: str
    signature: str

class OneTimePreKeyIn(BaseModel):
    key_id: int
    public_key: str

class UploadPreKeyBundle(BaseModel):
    identity_key: str
    signed_prekey: SignedPreKeyIn
    one_time_prekeys: List[OneTimePreKeyIn]
    device_id: int = 1

class PeerBundleResponse(BaseModel):
    user_id: str
    device_id: int
    registration_id: int
    identity_key: str
    signed_prekey: SignedPreKeyIn
    one_time_prekey: Optional[OneTimePreKeyIn] = None

@router.put("/bundle")
async def upload_keys(
    bundle: UploadPreKeyBundle,
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db)
):
    stmt = select(Device).where(Device.user_id == user_id, Device.device_id == bundle.device_id)
    res = await db.execute(stmt)
    device = res.scalar_one_or_none()
    if not device:
        raise HTTPException(status_code=404, detail="Device not found")

    stmt = select(IdentityKey).where(IdentityKey.device_id == device.id)
    idk_res = await db.execute(stmt)
    idk = idk_res.scalar_one_or_none()
    if idk:
        idk.public_key = bundle.identity_key
    else:
        db.add(IdentityKey(device_id=device.id, public_key=bundle.identity_key))

    spk = SignedPreKey(
        device_id=device.id,
        key_id=bundle.signed_prekey.key_id,
        public_key=bundle.signed_prekey.public_key,
        signature=bundle.signed_prekey.signature
    )
    db.add(spk)

    for otpk in bundle.one_time_prekeys:
        db.add(OneTimePreKey(
            device_id=device.id,
            key_id=otpk.key_id,
            public_key=otpk.public_key,
            consumed=False
        ))

    await db.commit()
    return {"status": "ok", "otpk_count": len(bundle.one_time_prekeys)}

@router.get("/bundle/{peer_user_id}", response_model=PeerBundleResponse)
async def get_peer_bundle(
    peer_user_id: str,
    device_id: int = 1,
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db)
):
    stmt = select(Device).where(Device.user_id == peer_user_id, Device.device_id == device_id)
    dev_res = await db.execute(stmt)
    device = dev_res.scalar_one_or_none()
    if not device:
        raise HTTPException(status_code=404, detail="Peer device not found")

    stmt = select(IdentityKey).where(IdentityKey.device_id == device.id)
    idk_res = await db.execute(stmt)
    idk = idk_res.scalar_one_or_none()
    if not idk:
        raise HTTPException(status_code=404, detail="Identity key not registered")

    stmt = select(SignedPreKey).where(SignedPreKey.device_id == device.id).order_by(SignedPreKey.created_at.desc())
    spk_res = await db.execute(stmt)
    spk = spk_res.scalars().first()
    if not spk:
        raise HTTPException(status_code=404, detail="Signed prekey missing")

    otpk_out = None
    while True:
        stmt = (
            select(OneTimePreKey)
            .where(OneTimePreKey.device_id == device.id, OneTimePreKey.consumed == False)
            .order_by(OneTimePreKey.id.asc())
            .limit(1)
        )
        otpk_res = await db.execute(stmt)
        otpk = otpk_res.scalar_one_or_none()
        if not otpk:
            break

        claim_stmt = (
            update(OneTimePreKey)
            .where(OneTimePreKey.id == otpk.id, OneTimePreKey.consumed == False)
            .values(consumed=True)
        )
        claim_res = await db.execute(claim_stmt)
        if claim_res.rowcount == 1:
            await db.commit()
            otpk_out = OneTimePreKeyIn(key_id=otpk.key_id, public_key=otpk.public_key)
            break

    return PeerBundleResponse(
        user_id=peer_user_id,
        device_id=device_id,
        registration_id=device.registration_id,
        identity_key=idk.public_key,
        signed_prekey=SignedPreKeyIn(
            key_id=spk.key_id,
            public_key=spk.public_key,
            signature=spk.signature
        ),
        one_time_prekey=otpk_out
    )

@router.get("/prekey-count")
async def get_remaining_prekeys(
    device_id: int = 1,
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db)
):
    stmt = select(Device).where(Device.user_id == user_id, Device.device_id == device_id)
    dev_res = await db.execute(stmt)
    device = dev_res.scalar_one_or_none()
    if not device:
        return {"remaining": 0}

    stmt = select(func.count(OneTimePreKey.id)).where(
        OneTimePreKey.device_id == device.id,
        OneTimePreKey.consumed == False
    )
    count_res = await db.execute(stmt)
    return {"remaining": count_res.scalar() or 0}
