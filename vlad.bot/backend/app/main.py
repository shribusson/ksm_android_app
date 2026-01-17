from fastapi import FastAPI
from contextlib import asynccontextmanager
from fastapi.middleware.cors import CORSMiddleware
from app.database import engine, AsyncSessionLocal
from app.config import get_settings
from app.routers import bots, scripts, webhooks, auth, employees
from app.utils import create_default_user
from app.middleware.logging import LoggingMiddleware
import logging

# Configure root logger
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# Note: In production, run migrations via CLI 'alembic upgrade head'
# For this MVP Self-Contained Docker, we can try to run them here OR just rely on manual exec.
# We will just create user here.

@asynccontextmanager
async def lifespan(app: FastAPI):
    print("Startup: Checking database...")
    
    # Create tables if they don't exist
    from app.models import Base
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    print("✓ Database tables created/verified")
    
    # Create default user
    async with AsyncSessionLocal() as db:
        await create_default_user(db)
    
    yield
    await engine.dispose()

app = FastAPI(
    title="HR Bot Platform",
    version="1.0.0",
    lifespan=lifespan
)

# CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Add logging middleware
app.add_middleware(LoggingMiddleware)

logger.info("🚀 HR Bot Platform starting...")

app.include_router(auth.router)
app.include_router(bots.router)
app.include_router(scripts.router)
app.include_router(employees.router)
from fastapi.staticfiles import StaticFiles
from fastapi import UploadFile, File
import shutil
import os
import uuid

# ... imports ...

app.include_router(webhooks.router)

# Mount Static
os.makedirs("app/static/uploads", exist_ok=True)
app.mount("/static", StaticFiles(directory="app/static"), name="static")

@app.post("/upload")
async def upload_file(file: UploadFile = File(...)):
    file_ext = os.path.splitext(file.filename)[1]
    filename = f"{uuid.uuid4()}{file_ext}"
    file_path = f"app/static/uploads/{filename}"
    
    with open(file_path, "wb") as buffer:
        shutil.copyfileobj(file.file, buffer)
        
    # Return absolute URL or relative? Frontend needs full URL usually if separate domains, 
    # but here same domain/port via proxy? Or dev setup.
    # In docker, backend is on port 8000. Frontend on 5173.
    # We should return a relative path or full URL.
    # For now: return relative path "/static/uploads/..." and let frontend handle generic domain 
    # OR return full url if we know it.
    # Let's return "/api/static/uploads/..." if proxied, or just "/static/..." if direct.
    # Since we are using Create React App / Vite proxying "/api", we might need to proxy "/static" too.
    # Or just return full URL based on request.base_url
    return {"url": f"/static/uploads/{filename}", "filename": filename}


@app.get("/")
async def root():
    return {"message": "HR Bot Platform API is running", "env": get_settings().DATABASE_URL[:10] + "***"}

@app.get("/health")
async def health_check():
    return {"status": "healthy"}
