from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, update
from app.database import get_db
from app.models import Script, Bot
from app.auth import get_current_user
from pydantic import BaseModel, Json
from datetime import datetime

router = APIRouter(prefix="/bots/{bot_id}/scripts", tags=["scripts"])

class ScriptBase(BaseModel):
    graph_data: dict # React Flow JSON

class ScriptResponse(ScriptBase):
    id: int
    bot_id: int
    version: int
    is_published: bool
    created_at: datetime

    class Config:
        from_attributes = True

@router.post("/draft", response_model=ScriptResponse)
async def save_draft(
    bot_id: int,
    script_in: ScriptBase,
    db: AsyncSession = Depends(get_db),
    user: str = Depends(get_current_user)
):
    # Check if bot exists
    bot = await db.get(Bot, bot_id)
    if not bot:
        raise HTTPException(status_code=404, detail="Bot not found")

    # Find latest draft (unpublished) or create new
    # For MVP: simple logic, always create new version on save or update latest draft?
    # Requirement: "editing goes in draft".
    # Let's find the latest version.
    query = select(Script).where(Script.bot_id == bot_id).order_by(Script.version.desc()).limit(1)
    result = await db.execute(query)
    latest_script = result.scalar_one_or_none()
    
    next_version = 1
    if latest_script:
        if not latest_script.is_published:
            # Update existing draft
            latest_script.graph_data = script_in.graph_data
            await db.commit()
            await db.refresh(latest_script)
            return latest_script
        next_version = latest_script.version + 1

    # Create new draft version
    new_script = Script(
        bot_id=bot_id,
        version=next_version,
        is_published=False,
        graph_data=script_in.graph_data
    )
    db.add(new_script)
    await db.commit()
    await db.refresh(new_script)
    return new_script

@router.post("/publish", response_model=ScriptResponse)
async def publish_script(
    bot_id: int,
    db: AsyncSession = Depends(get_db),
    user: str = Depends(get_current_user)
):
    # Find latest script (should be a draft usually)
    query = select(Script).where(Script.bot_id == bot_id).order_by(Script.version.desc()).limit(1)
    result = await db.execute(query)
    latest_script = result.scalar_one_or_none()

    if not latest_script:
        raise HTTPException(status_code=404, detail="No script found to publish")
    
    if latest_script.is_published:
        raise HTTPException(status_code=400, detail="Latest version is already published")

    latest_script.is_published = True
    
    # Update Bot's active version
    bot = await db.get(Bot, bot_id)
    bot.active_script_version = latest_script.version
    
    await db.commit()
    await db.refresh(latest_script)
    return latest_script

@router.get("/latest", response_model=ScriptResponse)
async def get_latest_script(
    bot_id: int,
    db: AsyncSession = Depends(get_db),
    user: str = Depends(get_current_user)
):
    query = select(Script).where(Script.bot_id == bot_id).order_by(Script.version.desc()).limit(1)
    result = await db.execute(query)
    script = result.scalar_one_or_none()
    if not script:
        # Return empty default
        return ScriptResponse(
            id=0, bot_id=bot_id, version=0, is_published=False, 
            graph_data={"nodes": [], "edges": []}, created_at=datetime.now()
        )
    return script
