from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from contextlib import asynccontextmanager
from app.database import init_db
from app.auth.routes import router as auth_router
from app.keys.routes import router as keys_router
from app.messages.routes import router as messages_router
from app.media.routes import router as media_router
from app.sync.routes import router as sync_router
from app.ws.routes import router as ws_router

@asynccontextmanager
async def lifespan(app: FastAPI):
    # Setup tables automatically if not already run by alembic
    try:
        await init_db()
    except Exception as e:
        print(f"Database auto-init notice: {e}")
    yield

app = FastAPI(
    title="Nido Relay Server",
    description="Zero-plaintext relay for Signal E2EE messaging, encrypted clip attachments, and music library sync.",
    version="1.0.0",
    lifespan=lifespan
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(auth_router)
app.include_router(keys_router)
app.include_router(messages_router)
app.include_router(media_router)
app.include_router(sync_router)
app.include_router(ws_router)

@app.get("/")
async def root():
    return {"name": "Nido Relay Server", "status": "running", "e2ee": True}
