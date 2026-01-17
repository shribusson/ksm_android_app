from fastapi import APIRouter, Depends, HTTPException, Response
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, delete
from pydantic import BaseModel
from app.database import get_db
from app.models import Bot
from app.auth import get_current_user
from app.services.engine import FlowEngine
from app.models import Script, Respondent, Session, Answer, ChannelConfig
from datetime import datetime
from app.services.export import ExportService

router = APIRouter(
    prefix="/bots",
    tags=["bots"],
    responses={404: {"description": "Not found"}},
)

@router.delete("/{bot_id}")
async def delete_bot(
    bot_id: int,
    db: AsyncSession = Depends(get_db),
    user: str = Depends(get_current_user)
):
    """Delete a bot and all its related data manually (to safely handle FKs without DB migration)"""
    # 1. Check if bot exists
    result = await db.execute(select(Bot).where(Bot.id == bot_id))
    bot = result.scalars().first()
    if not bot:
        raise HTTPException(status_code=404, detail="Bot not found")
    
    # 2. Manual Cleanup (in case DB cascade isn't applied)
    # Answers (via Session or Respondent) 
    # Hard to select all answers efficiently without subqueries, but let's try cascading by session.
    
    # Get all respondent IDs for this bot
    resp_res = await db.execute(select(Respondent.id).where(Respondent.bot_id == bot_id))
    respondent_ids = resp_res.scalars().all()
    
    if respondent_ids:
        # Delete Answers linked to these respondents
        await db.execute(delete(Answer).where(Answer.respondent_id.in_(respondent_ids)))
        
        # Delete Sessions linked to these respondents (or bot_id if schema updated)
        await db.execute(delete(Session).where(Session.respondent_id.in_(respondent_ids)))
        
        # Delete Respondents
        await db.execute(delete(Respondent).where(Respondent.id.in_(respondent_ids)))

    # Delete Scripts
    await db.execute(delete(Script).where(Script.bot_id == bot_id))
    
    # Delete Channel Configs
    await db.execute(delete(ChannelConfig).where(ChannelConfig.bot_id == bot_id))

    # 3. Finally delete the bot
    await db.delete(bot)
    await db.commit()
    return {"message": "Bot deleted successfully"}

# ...

class ChatMessage(BaseModel):
    session_id: int
    text: str

@router.post("/{bot_id}/chat/start")
async def start_preview_chat(
    bot_id: int,
    db: AsyncSession = Depends(get_db),
    user: str = Depends(get_current_user)
):
    try:
        # 1. Get Latest Script (Draft)
        # Logic: get max ID or max version.
        # Assuming 'latest' is what we edit.
        res = await db.execute(select(Script).where(Script.bot_id == bot_id).order_by(Script.id.desc()))
        script = res.scalars().first()
        if not script:
            raise HTTPException(status_code=404, detail="No script found for bot")

        # 2. Get/Create Test Respondent
        # unique test respondent for this user?
        # For MVP, single 'Test User' per Bot.
        print(f"Looking for test respondent for bot {bot_id}")
        res = await db.execute(select(Respondent).where(Respondent.bot_id == bot_id, Respondent.external_id == "test_user"))
        respondent = res.scalars().first()
        if not respondent:
            print("Creating new test respondent...")
            respondent = Respondent(
                bot_id=bot_id,
                channel_type="web",
                external_id="test_user",
                profile_data={"name": "Tester"}
            )
            db.add(respondent)
            await db.commit()
            await db.refresh(respondent)
        else:
            print(f"Found existing respondent: {respondent.id}")

        # 3. Create Session
        print("Creating new session...")
        session = Session(
            bot_id=bot_id,
            respondent_id=respondent.id,
            script_id=script.id,
            state="active",
            variables={}
        )
        db.add(session)
        await db.commit()
        await db.refresh(session)
        print(f"Session created: {session.id}")

        # 4. Trigger Start Node
        print("Triggering engine process_step...")
        engine = FlowEngine(db)
        await engine.process_step(session)
        print(f"Engine finished. Messages: {len(engine.captured_messages)}")
        
        return {"session_id": session.id, "messages": engine.captured_messages}
        
    except Exception as e:
        import traceback
        traceback.print_exc()
        print(f"ERROR in start_preview_chat: {str(e)}")
        raise HTTPException(status_code=500, detail=f"Server Error: {str(e)}")

@router.post("/{bot_id}/chat/message")
async def send_chat_message(
    bot_id: int,
    msg: ChatMessage,
    db: AsyncSession = Depends(get_db),
    user: str = Depends(get_current_user)
):
    session = await db.get(Session, msg.session_id)
    if not session:
        raise HTTPException(404, "Session not found")
        
    engine = FlowEngine(db)
    await engine.process_step(session, user_input=msg.text)
    
    return {"messages": engine.captured_messages}

class BotCreate(BaseModel):
    name: str
    description: str | None = None
    trigger_keywords: str | None = None

class BotResponse(BotCreate):
    id: int
    status: str
    trigger_keywords: str | None
    created_at: datetime
    updated_at: datetime | None

    class Config:
        from_attributes = True

@router.post("/", response_model=BotResponse)
async def create_bot(
    bot_in: BotCreate,
    db: AsyncSession = Depends(get_db),
    user: str = Depends(get_current_user)
):
    new_bot = Bot(
        name=bot_in.name, 
        description=bot_in.description,
        trigger_keywords=bot_in.trigger_keywords
    )
    db.add(new_bot)
    await db.commit()
    await db.refresh(new_bot)
    return new_bot

@router.get("/", response_model=list[BotResponse])
async def list_bots(
    db: AsyncSession = Depends(get_db),
    user: str = Depends(get_current_user)
):
    result = await db.execute(select(Bot).order_by(Bot.created_at.desc()))
    return result.scalars().all()

@router.get("/{bot_id}/export")
async def export_bot_data(
    bot_id: int,
    db: AsyncSession = Depends(get_db),
    user: str = Depends(get_current_user)
):
    service = ExportService(db)
    file_stream = await service.generate_excel(bot_id)
    
    headers = {
        'Content-Disposition': f'attachment; filename="bot_{bot_id}_export.xlsx"'
    }
    return Response(content=file_stream.getvalue(), media_type="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", headers=headers)

@router.get("/{bot_id}/results")
async def get_bot_results(
    bot_id: int,
    db: AsyncSession = Depends(get_db),
    user: str = Depends(get_current_user)
):
    """
    Returns data for the results table with employee metadata:
    [
      { 
        "session_id": 1, 
        "state": "finished", 
        "employee": { "name": "Иван", "position": "Developer", "department": "IT", "tags": ["onboarding"] },
        "answers": { "q_name": "Vlad", "q_age": "30" },
        ...
      },
      ...
    ]
    """
    from app.models import Session, Respondent, Answer, Employee
    
    # 1. Fetch Sessions with Respondent data
    query = select(Session, Respondent).join(Respondent).where(Respondent.bot_id == bot_id).order_by(Session.started_at.desc())
    result = await db.execute(query)
    session_respondent_pairs = result.all()
    
    if not session_respondent_pairs:
        return []
    
    # 2. Get all respondent external_ids to match with employees
    external_ids = set()
    for session, respondent in session_respondent_pairs:
        if respondent.external_id:
            external_ids.add(respondent.external_id)
    
    # 3. Fetch matching employees (by telegram_id or phone)
    employee_map = {}
    if external_ids:
        emp_query = select(Employee).where(
            (Employee.telegram_id.in_(external_ids)) | 
            (Employee.phone.in_(external_ids))
        )
        emp_result = await db.execute(emp_query)
        for emp in emp_result.scalars().all():
            if emp.telegram_id:
                employee_map[emp.telegram_id] = emp
            if emp.phone:
                employee_map[emp.phone] = emp
    
    # 4. Fetch Answers
    s_ids = [s.id for s, r in session_respondent_pairs]
    a_query = select(Answer).where(Answer.session_id.in_(s_ids))
    a_result = await db.execute(a_query)
    answers = a_result.scalars().all()
    
    # 5. Map Answers by session_id
    data_map = {}
    for ans in answers:
        if ans.session_id not in data_map:
            data_map[ans.session_id] = {}
        data_map[ans.session_id][ans.question_key] = ans.value
    
    # 6. Build Response with employee data
    response_data = []
    for session, respondent in session_respondent_pairs:
        # Find matching employee
        employee = employee_map.get(respondent.external_id)
        employee_data = None
        if employee:
            employee_data = {
                "id": employee.id,
                "name": employee.full_name,
                "position": employee.position,
                "department": employee.department,
                "tags": employee.tags.split(",") if employee.tags else [],
            }
        
        response_data.append({
            "session_id": session.id,
            "state": session.state,
            "started_at": session.started_at,
            "finished_at": session.finished_at,
            "respondent_id": respondent.id,
            "channel": respondent.channel_type,
            "external_id": respondent.external_id,
            "employee": employee_data,
            "variables": session.variables,
            "answers": data_map.get(session.id, {})
        })
    
    return response_data
