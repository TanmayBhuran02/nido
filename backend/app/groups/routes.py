import uuid
from typing import List, Optional
from datetime import datetime
from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, delete, and_
from app.database import get_db
from app.models import Group, GroupMember, User, Device, PendingMessage
from app.auth.security import get_current_user_id
from app.ws.routes import manager

router = APIRouter(prefix="/api/v1/groups", tags=["groups"])

class CreateGroupRequest(BaseModel):
    name: str = Field(..., min_length=1, max_length=128)
    avatar_url: Optional[str] = None
    initial_members: List[str] = []

class GroupMemberOut(BaseModel):
    user_id: str
    username: str
    role: str
    joined_at: str

class GroupOut(BaseModel):
    id: str
    name: str
    avatar_url: Optional[str]
    created_by: Optional[str]
    created_at: str
    members: List[GroupMemberOut] = []

class AddMemberRequest(BaseModel):
    user_id: str
    role: str = "MEMBER"

class SendGroupMessageRequest(BaseModel):
    ciphertext: str = Field(..., max_length=65536)
    message_type: int = 1
    sender_device_id: int = 1
    reply_to_id: Optional[str] = None

@router.post("", response_model=GroupOut, status_code=status.HTTP_201_CREATED)
async def create_group(
    req: CreateGroupRequest,
    current_user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db)
):
    group = Group(
        id=str(uuid.uuid4()),
        name=req.name,
        avatar_url=req.avatar_url,
        created_by=current_user_id
    )
    db.add(group)

    # Add creator as ADMIN
    admin_member = GroupMember(
        id=str(uuid.uuid4()),
        group_id=group.id,
        user_id=current_user_id,
        role="ADMIN"
    )
    db.add(admin_member)

    # Add initial members
    for uid in req.initial_members:
        if uid != current_user_id:
            db.add(GroupMember(
                id=str(uuid.uuid4()),
                group_id=group.id,
                user_id=uid,
                role="MEMBER"
            ))

    await db.commit()
    await db.refresh(group)
    return await get_group_details(group.id, current_user_id, db)

@router.get("", response_model=List[GroupOut])
async def list_user_groups(
    current_user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db)
):
    # Find all groups user belongs to
    member_stmt = select(GroupMember.group_id).where(GroupMember.user_id == current_user_id)
    member_res = await db.execute(member_stmt)
    group_ids = [row[0] for row in member_res.all()]

    if not group_ids:
        return []

    results = []
    for gid in group_ids:
        grp = await get_group_details(gid, current_user_id, db)
        results.append(grp)
    return results

@router.get("/{group_id}", response_model=GroupOut)
async def get_group_details(
    group_id: str,
    current_user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db)
):
    grp_stmt = select(Group).where(Group.id == group_id)
    grp_res = await db.execute(grp_stmt)
    group = grp_res.scalar_one_or_none()
    if not group:
        raise HTTPException(status_code=404, detail="Group not found")

    # Verify membership
    mem_check = await db.execute(
        select(GroupMember).where(GroupMember.group_id == group_id, GroupMember.user_id == current_user_id)
    )
    if not mem_check.scalar_one_or_none():
        raise HTTPException(status_code=403, detail="Not a group member")

    members_stmt = (
        select(GroupMember, User.username)
        .join(User, GroupMember.user_id == User.id)
        .where(GroupMember.group_id == group_id)
    )
    members_res = await db.execute(members_stmt)
    members_data = []
    for member, username in members_res.all():
        members_data.append(
            GroupMemberOut(
                user_id=member.user_id,
                username=username,
                role=member.role,
                joined_at=member.joined_at.isoformat()
            )
        )

    return GroupOut(
        id=group.id,
        name=group.name,
        avatar_url=group.avatar_url,
        created_by=group.created_by,
        created_at=group.created_at.isoformat(),
        members=members_data
    )

@router.post("/{group_id}/members", status_code=status.HTTP_201_CREATED)
async def add_group_member(
    group_id: str,
    req: AddMemberRequest,
    current_user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db)
):
    # Verify current user is admin
    admin_stmt = select(GroupMember).where(
        GroupMember.group_id == group_id,
        GroupMember.user_id == current_user_id,
        GroupMember.role == "ADMIN"
    )
    admin_res = await db.execute(admin_stmt)
    if not admin_res.scalar_one_or_none():
        raise HTTPException(status_code=403, detail="Only admins can add members")

    # Check target user exists
    user_res = await db.execute(select(User).where(User.id == req.user_id))
    if not user_res.scalar_one_or_none():
        raise HTTPException(status_code=404, detail="User not found")

    # Check not already member
    exist_res = await db.execute(
        select(GroupMember).where(GroupMember.group_id == group_id, GroupMember.user_id == req.user_id)
    )
    if exist_res.scalar_one_or_none():
        raise HTTPException(status_code=400, detail="User already in group")

    new_mem = GroupMember(
        id=str(uuid.uuid4()),
        group_id=group_id,
        user_id=req.user_id,
        role=req.role
    )
    db.add(new_mem)
    await db.commit()

    # Broadcast membership change notification event to active connections
    for member in (await db.execute(select(GroupMember.user_id).where(GroupMember.group_id == group_id))).all():
        uid = member[0]
        await manager.send_to_device(uid, 1, {
            "type": "group_update",
            "group_id": group_id,
            "action": "member_added",
            "user_id": req.user_id
        })

    return {"status": "member_added"}

@router.delete("/{group_id}/members/{user_id}")
async def remove_group_member(
    group_id: str,
    user_id: str,
    current_user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db)
):
    # Can remove self or if admin can remove others
    if current_user_id != user_id:
        admin_res = await db.execute(
            select(GroupMember).where(
                GroupMember.group_id == group_id,
                GroupMember.user_id == current_user_id,
                GroupMember.role == "ADMIN"
            )
        )
        if not admin_res.scalar_one_or_none():
            raise HTTPException(status_code=403, detail="Only admins can remove other members")

    del_stmt = delete(GroupMember).where(
        GroupMember.group_id == group_id,
        GroupMember.user_id == user_id
    )
    await db.execute(del_stmt)
    await db.commit()

    # Broadcast member removal so remaining members rotate sender keys
    rem_members = (await db.execute(select(GroupMember.user_id).where(GroupMember.group_id == group_id))).all()
    for member in rem_members:
        uid = member[0]
        await manager.send_to_device(uid, 1, {
            "type": "group_update",
            "group_id": group_id,
            "action": "member_removed",
            "user_id": user_id,
            "require_key_rotation": True
        })

    return {"status": "member_removed"}

@router.post("/{group_id}/messages")
async def send_group_message(
    group_id: str,
    req: SendGroupMessageRequest,
    current_user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db)
):
    # Verify membership
    mem_stmt = select(GroupMember.user_id).where(GroupMember.group_id == group_id)
    mem_res = await db.execute(mem_stmt)
    members = [r[0] for r in mem_res.all()]
    if current_user_id not in members:
        raise HTTPException(status_code=403, detail="Not a group member")

    recipients = [m for m in members if m != current_user_id]
    queued_ids = []

    # Fan out to all recipients and their devices
    now = datetime.utcnow()
    for recipient_id in recipients:
        dev_res = await db.execute(select(Device.device_id).where(Device.user_id == recipient_id))
        devices = [d[0] for d in dev_res.all()] or [1]
        for dev_id in devices:
            pending = PendingMessage(
                id=str(uuid.uuid4()),
                recipient_user_id=recipient_id,
                recipient_device_id=dev_id,
                sender_user_id=current_user_id,
                sender_device_id=req.sender_device_id,
                ciphertext=req.ciphertext,
                message_type=req.message_type,
                group_id=group_id,
                reply_to_id=req.reply_to_id,
                created_at=now
            )
            db.add(pending)
            queued_ids.append(pending.id)

            # Live WS delivery
            envelope = {
                "type": "message",
                "id": pending.id,
                "group_id": group_id,
                "sender_user_id": current_user_id,
                "sender_device_id": req.sender_device_id,
                "ciphertext": req.ciphertext,
                "message_type": req.message_type,
                "reply_to_id": req.reply_to_id,
                "created_at": now.isoformat()
            }
            await manager.send_to_device(recipient_id, dev_id, envelope)

    await db.commit()
    return {"status": "dispatched", "count": len(queued_ids)}
