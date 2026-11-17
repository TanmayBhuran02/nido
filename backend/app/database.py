from typing import AsyncGenerator
from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker, AsyncSession
from app.config import settings
from app.models import Base

engine = create_async_engine(settings.DATABASE_URL, echo=False, future=True)
async_session = async_sessionmaker(engine, class_=AsyncSession, expire_on_commit=False)

async def init_db():
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
        def _migrate(sync_conn):
            from sqlalchemy import text
            try:
                rows = sync_conn.execute(text("PRAGMA table_info(users)")).fetchall()
                cols = [r[1] for r in rows]
                if cols and "email" not in cols:
                    sync_conn.execute(text("ALTER TABLE users ADD COLUMN email VARCHAR(255)"))
            except Exception:
                pass
        await conn.run_sync(_migrate)

async def get_db() -> AsyncGenerator[AsyncSession, None]:
    async with async_session() as session:
        try:
            yield session
        finally:
            await session.close()
