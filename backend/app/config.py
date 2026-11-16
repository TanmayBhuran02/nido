import os
from pathlib import Path
from pydantic_settings import BaseSettings

data_dir = os.environ.get("DATA_DIR", ".")
os.makedirs(data_dir, exist_ok=True)
db_file = Path(data_dir) / "nido_backend.db"
storage_path = str(Path(data_dir) / "media_storage")
sqlite_url = f"sqlite+aiosqlite:///{db_file.resolve().as_posix()}"

class Settings(BaseSettings):
    DATABASE_URL: str = sqlite_url
    REDIS_URL: str = "redis://localhost:6379/0"
    MINIO_ENDPOINT: str = "localhost:9000"
    MINIO_ACCESS_KEY: str = "minioadmin"
    MINIO_SECRET_KEY: str = "minioadminpassword"
    MINIO_BUCKET: str = "nido-blobs"
    MINIO_USE_SSL: bool = False
    USE_LOCAL_STORAGE: bool = True
    LOCAL_STORAGE_DIR: str = storage_path
    JWT_SECRET: str = "supersecretrelaykeythatislongenough256bit"
    JWT_ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 60 * 24 * 7
    REFRESH_TOKEN_EXPIRE_DAYS: int = 30

    class Config:
        env_file = ".env"

settings = Settings()
