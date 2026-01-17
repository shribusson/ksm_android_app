from fastapi import APIRouter, Depends, HTTPException, Request, BackgroundTasks
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from app.database import get_db
from app.models import Bot, ChannelConfig, Respondent, Session, Script
from app.services.engine import FlowEngine
import logging

# Simple logger
logger = logging.getLogger(__name__)

router = APIRouter(prefix="/webhooks", tags=["webhooks"])

async def get_or_create_respondent(db: AsyncSession, bot_id: int, channel: str, external_id: str):
    query = select(Respondent).where(
        Respondent.bot_id == bot_id,
        Respondent.channel_type == channel,
        Respondent.external_id == external_id
    )
    result = await db.execute(query)
    resp = result.scalar_one_or_none()
    if not resp:
        resp = Respondent(bot_id=bot_id, channel_type=channel, external_id=external_id)
        db.add(resp)
        await db.commit()
        await db.refresh(resp)
    return resp

async def get_active_session(db: AsyncSession, respondent_id: int):
    query = select(Session).where(
        Session.respondent_id == respondent_id,
        Session.state == "active"
    ).order_by(Session.started_at.desc()).limit(1)
    result = await db.execute(query)
    return result.scalar_one_or_none()

async def process_event_background(bot_id: int, channel: str, external_id: str, text: str, db: AsyncSession):
    # 1. Find Respondent
    resp = await get_or_create_respondent(db, bot_id, channel, external_id)
    
    # 2. Find Session
    session = await get_active_session(db, resp.id)
    
    if not session:
        # Start new session? 
        # Only if we trigger a start? Or always if we have an active script?
        # Find active bot script
        bot = await db.get(Bot, bot_id)
        if not bot or not bot.active_script_version:
             logger.warning(f"Bot {bot_id} has no active script")
             return

        # Find script ID
        s_query = select(Script).where(Script.bot_id == bot_id, Script.version == bot.active_script_version)
        s_result = await db.execute(s_query)
        script = s_result.scalar_one_or_none()
        
        if not script:
             return

        session = Session(
            respondent_id=resp.id,
            script_id=script.id,
            state="active",
            variables={"external_id": external_id, "channel": channel},
            current_node_id=None # Start
        )
        db.add(session)
        await db.commit()
        await db.refresh(session)
    
    # 3. Engine
    engine = FlowEngine(db)
    await engine.process_step(session, user_input=text)


@router.post("/telegram/{bot_id}")
async def telegram_webhook(bot_id: int, request: Request, background_tasks: BackgroundTasks, db: AsyncSession = Depends(get_db)):
    data = await request.json()
    
    # Check if message
    if "message" in data:
        msg = data["message"]
        chat_id = str(msg.get("chat", {}).get("id"))
        text = msg.get("text", "")
        
        # Process in background to return 200 OK quickly to Telegram
        # Passing DB session to background task is tricky in FastAPI (dependency injection closes it).
        # Better pattern: parse here, put in queue, or await.
        # For MVP, we await (Telegram allows few seconds) or use a fresh generator.
        # Risk: db session closed.
        await process_event_background(bot_id, "telegram", chat_id, text, db)
        
    return {"status": "ok"}

@router.get("/whatsapp/{bot_id}")
async def whatsapp_verify(bot_id: int, request: Request):
    # Meta Challenge verification
    query = request.query_params
    mode = query.get("hub.mode")
    token = query.get("hub.verify_token")
    challenge = query.get("hub.challenge")
    
    # Fetch Verify Token from DB? For MVP, assume it matches a config or just return challenge
    if mode == "subscribe" and challenge:
        return int(challenge)
    return {"status": "error"}

@router.post("/whatsapp/{bot_id}")
async def whatsapp_webhook(bot_id: int, request: Request, db: AsyncSession = Depends(get_db)):
    data = await request.json()
    # Parse WA Message
    # ...
    return {"status": "received"}
