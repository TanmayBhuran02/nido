import uuid
from datetime import datetime
from sqlalchemy import Column, String, Integer, Boolean, DateTime, ForeignKey, BigInteger, Text
from sqlalchemy.orm import declarative_base, relationship

Base = declarative_base()

class User(Base):
    __tablename__ = "users"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    username = Column(String(64), unique=True, index=True, nullable=False)
    phone = Column(String(32), unique=True, nullable=True)
    password_hash = Column(String(255), nullable=False)
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)

    devices = relationship("Device", back_populates="user", cascade="all, delete-orphan")
    synced_tracks = relationship("SyncedTrack", back_populates="user", cascade="all, delete-orphan")

class Device(Base):
    __tablename__ = "devices"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    user_id = Column(String(36), ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    device_id = Column(Integer, default=1, nullable=False)
    registration_id = Column(Integer, nullable=False)
    fcm_token = Column(Text, nullable=True)
    last_seen_at = Column(DateTime, default=datetime.utcnow, nullable=False)

    user = relationship("User", back_populates="devices")
    identity_key = relationship("IdentityKey", back_populates="device", uselist=False, cascade="all, delete-orphan")
    signed_prekeys = relationship("SignedPreKey", back_populates="device", cascade="all, delete-orphan")
    one_time_prekeys = relationship("OneTimePreKey", back_populates="device", cascade="all, delete-orphan")

class IdentityKey(Base):
    __tablename__ = "identity_keys"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    device_id = Column(String(36), ForeignKey("devices.id", ondelete="CASCADE"), unique=True, nullable=False)
    public_key = Column(Text, nullable=False)

    device = relationship("Device", back_populates="identity_key")

class SignedPreKey(Base):
    __tablename__ = "signed_prekeys"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    device_id = Column(String(36), ForeignKey("devices.id", ondelete="CASCADE"), nullable=False, index=True)
    key_id = Column(Integer, nullable=False)
    public_key = Column(Text, nullable=False)
    signature = Column(Text, nullable=False)
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)

    device = relationship("Device", back_populates="signed_prekeys")

class OneTimePreKey(Base):
    __tablename__ = "one_time_prekeys"

    id = Column(Integer, primary_key=True, autoincrement=True)
    device_id = Column(String(36), ForeignKey("devices.id", ondelete="CASCADE"), nullable=False, index=True)
    key_id = Column(Integer, nullable=False)
    public_key = Column(Text, nullable=False)
    consumed = Column(Boolean, default=False, nullable=False)

    device = relationship("Device", back_populates="one_time_prekeys")

class PendingMessage(Base):
    __tablename__ = "pending_messages"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    recipient_user_id = Column(String(36), nullable=False, index=True)
    recipient_device_id = Column(Integer, nullable=False)
    sender_user_id = Column(String(36), nullable=False)
    sender_device_id = Column(Integer, nullable=False)
    ciphertext = Column(Text, nullable=False)
    message_type = Column(Integer, default=1, nullable=False)
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)

class Blob(Base):
    __tablename__ = "blobs"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    owner_id = Column(String(36), nullable=False, index=True)
    size_bytes = Column(BigInteger, nullable=False)
    sha256_hash = Column(String(64), nullable=False)
    mime_type = Column(String(128), default="application/octet-stream", nullable=False)
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)
    expires_at = Column(DateTime, nullable=False)

class SyncedTrack(Base):
    __tablename__ = "synced_tracks"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    user_id = Column(String(36), ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    track_id = Column(String(255), nullable=False)
    title = Column(String(255), nullable=False)
    artist = Column(String(255), nullable=False)
    duration_ms = Column(BigInteger, nullable=False)
    downloaded_at = Column(DateTime, default=datetime.utcnow, nullable=False)

    user = relationship("User", back_populates="synced_tracks")
