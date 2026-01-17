from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from app.database import get_db
from app.models import SystemSetting
from app.auth import get_current_user
from pydantic import BaseModel

router = APIRouter(prefix="/settings", tags=["settings"])

class SettingsUpdate(BaseModel):
    settings: dict[str, str]

@router.get("/", response_model=dict[str, str])
async def get_settings(
    db: AsyncSession = Depends(get_db),
    user: str = Depends(get_current_user)
):
    result = await db.execute(select(SystemSetting))
    settings = result.scalars().all()
    return {s.key: s.value for s in settings}

@router.post("/")
async def update_settings(
    data: SettingsUpdate,
    db: AsyncSession = Depends(get_db),
    user: str = Depends(get_current_user)
):
    """
    Update multiple settings at once.
    Example: { "settings": { "telegram_token": "...", "whatsapp_token": "..." } }
    """
    for key, value in data.settings.items():
        # Check if exists
        result = await db.execute(select(SystemSetting).where(SystemSetting.key == key))
        existing = result.scalar_one_or_none()
        
        if existing:
            existing.value = value
        else:
            new_setting = SystemSetting(key=key, value=value)
            db.add(new_setting)
            
    await db.commit()
    return {"status": "ok"}
