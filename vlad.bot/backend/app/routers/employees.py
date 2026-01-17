from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, or_
from typing import List, Optional
from pydantic import BaseModel
from app.database import get_db
from app.models import Employee, User, Bot, Session, Respondent, Script
from app.services.engine import FlowEngine
from app.auth import get_current_user
import logging

logger = logging.getLogger(__name__)

router = APIRouter(
    prefix="/employees",
    tags=["employees"],
    responses={404: {"description": "Not found"}},
)

# Pydantic Models
class EmployeeCreate(BaseModel):
    full_name: str
    position: Optional[str] = None
    phone: Optional[str] = None
    telegram_id: Optional[str] = None
    department: Optional[str] = None
    tags: Optional[str] = None

class EmployeeUpdate(BaseModel):
    full_name: Optional[str] = None
    position: Optional[str] = None
    phone: Optional[str] = None
    telegram_id: Optional[str] = None
    department: Optional[str] = None
    tags: Optional[str] = None

class AssignRequest(BaseModel):
    bot_id: int

class AssignBulkRequest(BaseModel):
    bot_id: int
    department: Optional[str] = None
    tags: Optional[str] = None


@router.post("/{employee_id}/assign")
async def assign_bot(employee_id: int, request: AssignRequest, db: AsyncSession = Depends(get_db)):
    # 1. Get Employee
    employee = await db.get(Employee, employee_id)
    if not employee:
        raise HTTPException(status_code=404, detail="Employee not found")

    # 2. Get Bot
    bot = await db.get(Bot, request.bot_id)
    if not bot:
        raise HTTPException(status_code=404, detail="Bot not found")
        
    # Get Active Script
    if not bot.active_script_version:
         raise HTTPException(status_code=400, detail="Bot has no published version to assign.")
         
    script_res = await db.execute(select(Script).where(Script.bot_id == bot.id, Script.version == bot.active_script_version))
    script = script_res.scalar_one_or_none()
    
    if not script:
        # Fallback: Try finding ANY script? No, strict.
        raise HTTPException(status_code=400, detail="Published script version not found.")

    # 3. Resolve Respondent
    respondent = None
    if employee.telegram_id:
        res = await db.execute(select(Respondent).where(Respondent.telegram_id == employee.telegram_id))
        respondent = res.scalars().first()
        if not respondent:
            respondent = Respondent(telegram_id=employee.telegram_id, name=employee.full_name, platform="telegram", external_id=employee.telegram_id)
            db.add(respondent)
            await db.commit()
            await db.refresh(respondent)
            
    elif employee.phone:
        res = await db.execute(select(Respondent).where(Respondent.phone == employee.phone))
        respondent = res.scalars().first()
        if not respondent:
            respondent = Respondent(phone=employee.phone, name=employee.full_name, platform="whatsapp", external_id=employee.phone)
            db.add(respondent)
            await db.commit()
            await db.refresh(respondent)
            
    else:
        raise HTTPException(status_code=400, detail="Employee has no contact info")
        
    # 4. Create Session
    new_session = Session(
        bot_id=bot.id,
        respondent_id=respondent.id,
        script_id=script.id,
        state="active",
        context={}
    )
    db.add(new_session)
    await db.commit()
    
    return {"status": "assigned", "session_id": new_session.id}

class AssignBulkRequest(BaseModel):
    bot_id: int
    department: Optional[str] = None
    position: Optional[str] = None
    tags: Optional[str] = None

@router.post("/assign-bulk")
async def assign_bot_bulk(request: AssignBulkRequest, db: AsyncSession = Depends(get_db)):
    # 1. Get Bot
    bot = await db.get(Bot, request.bot_id)
    if not bot:
        raise HTTPException(status_code=404, detail="Bot not found")
        
    if not bot.active_script_version:
         raise HTTPException(status_code=400, detail="Bot has no published version.")

    script_res = await db.execute(select(Script).where(Script.bot_id == bot.id, Script.version == bot.active_script_version))
    script = script_res.scalar_one_or_none()
    if not script:
        raise HTTPException(status_code=400, detail="Published script not found.")
        
    # 2. Find Employees
    query = select(Employee)
    if request.department:
        query = query.where(Employee.department == request.department)
    if request.position:
        query = query.where(Employee.position == request.position)
    
    result = await db.execute(query)
    employees = result.scalars().all()
    
    if request.tags:
        required_tags = [t.strip().lower() for t in request.tags.split(',')]
        filtered = []
        for emp in employees:
            if not emp.tags: continue
            emp_tags = [t.strip().lower() for t in emp.tags.split(',')]
            if any(rt in emp_tags for rt in required_tags):
                filtered.append(emp)
        employees = filtered
        
    if not employees:
         return {"status": "no_employees_found", "count": 0}

    count = 0
    created_sessions = []
    
    for emp in employees:
        respondent = None
        # Logic duplicated for brevity, ideally refactor
        if emp.telegram_id:
            res = await db.execute(select(Respondent).where(Respondent.telegram_id == emp.telegram_id))
            respondent = res.scalars().first()
            if not respondent:
                respondent = Respondent(telegram_id=emp.telegram_id, name=emp.full_name, platform="telegram", external_id=emp.telegram_id)
                db.add(respondent)
                await db.commit() 
                await db.refresh(respondent)
        elif emp.phone:
            res = await db.execute(select(Respondent).where(Respondent.phone == emp.phone))
            respondent = res.scalars().first()
            if not respondent:
                respondent = Respondent(phone=emp.phone, name=emp.full_name, platform="whatsapp", external_id=emp.phone)
                db.add(respondent)
                await db.commit()
                await db.refresh(respondent)
        
        if respondent:
            new_session = Session(
                bot_id=bot.id,
                respondent_id=respondent.id,
                script_id=script.id,
                state="active",
                variables={}
            )
            db.add(new_session)
            created_sessions.append(new_session)
            count += 1
            
    await db.commit()
    
    return {"status": "assigned", "count": count}

# --- Pydantic Models ---
class EmployeeBase(BaseModel):
    full_name: str
    position: Optional[str] = None
    phone: Optional[str] = None
    telegram_id: Optional[str] = None
    department: Optional[str] = None
    tags: Optional[str] = None

class EmployeeCreate(EmployeeBase):
    pass

class EmployeeUpdate(EmployeeBase):
    pass

class EmployeeResponse(EmployeeBase):
    id: int
    created_at: str  # Simplify date for now

    class Config:
        from_attributes = True
        json_encoders = {
            # datetime: lambda v: v.isoformat()
        }

class AssignBulkRequest(BaseModel):
    bot_id: int
    department: Optional[str] = None
    tags: Optional[str] = None # Comma separated tags to filter

@router.post("/assign-bulk")
async def assign_bot_bulk(request: AssignBulkRequest, db: AsyncSession = Depends(get_db)):
    # 1. Get Bot
    bot = await db.get(Bot, request.bot_id)
    if not bot:
        raise HTTPException(status_code=404, detail="Bot not found")
        
    # 2. Find Employees
    query = select(Employee)
    if request.department:
        query = query.where(Employee.department == request.department)
    
    # 3. Fetch all potential matches (filtering tags in Python for simplicity with CSV field)
    result = await db.execute(query)
    employees = result.scalars().all()
    
    if request.tags:
        required_tags = [t.strip().lower() for t in request.tags.split(',')]
        filtered_employees = []
        for emp in employees:
            if not emp.tags:
                continue
            emp_tags = [t.strip().lower() for t in emp.tags.split(',')]
            # Check overlap
            if any(rt in emp_tags for rt in required_tags):
                filtered_employees.append(emp)
        employees = filtered_employees
        
    if not employees:
         return {"status": "no_employees_found", "count": 0}

    count = 0
    # 4. Assign
    # Loop manually. Optimize later (bulk insert).
    # Need to resolve Respondent for each.
    for emp in employees:
        # Resolve Respondent
        respondent = None
        # Try Telegram
        if emp.telegram_id:
            res = await db.execute(select(Respondent).where(Respondent.telegram_id == emp.telegram_id))
            respondent = res.scalars().first()
            if not respondent:
                respondent = Respondent(telegram_id=emp.telegram_id, name=emp.full_name, platform="telegram")
                db.add(respondent)
                await db.commit() # Commit to get ID
                await db.refresh(respondent)
        
        # Try Phone (fallback if no TG or if TG failed? Priority?)
        # If no TG, try Phone
        elif emp.phone:
            res = await db.execute(select(Respondent).where(Respondent.phone == emp.phone))
            respondent = res.scalars().first()
            if not respondent:
                respondent = Respondent(phone=emp.phone, name=emp.full_name, platform="whatsapp")
                db.add(respondent)
                await db.commit()
                await db.refresh(respondent)
        
        if respondent:
            # Create Session
            new_session = Session(
                bot_id=bot.id,
                respondent_id=respondent.id,
                state="active",
                context={}
            )
            db.add(new_session)
            count += 1
            
    await db.commit()
    return {"status": "assigned", "count": count}

@router.get("/", response_model=List[EmployeeResponse])
async def get_employees(
    department: Optional[str] = None,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user)
):
    query = select(Employee).order_by(Employee.full_name)
    if department:
        query = query.where(Employee.department == department)
    result = await db.execute(query)
    # Convert datetime to string manually if needed or rely on Pydantic
    employees = result.scalars().all()
    # Simple conversion to avoid Pydantic/SQLAlchemy datetime issues if any
    return [
        EmployeeResponse(
            id=e.id,
            full_name=e.full_name,
            position=e.position,
            phone=e.phone,
            telegram_id=e.telegram_id,
            department=e.department,
            tags=e.tags,
            created_at=e.created_at.isoformat() if e.created_at else ""
        ) for e in employees
    ]

@router.post("/", response_model=EmployeeResponse)
async def create_employee(
    employee: EmployeeCreate,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user)
):
    db_employee = Employee(**employee.dict())
    db.add(db_employee)
    await db.commit()
    await db.refresh(db_employee)
    return EmployeeResponse(
        id=db_employee.id,
        full_name=db_employee.full_name,
        position=db_employee.position,
        phone=db_employee.phone,
        telegram_id=db_employee.telegram_id,
        department=db_employee.department,
        tags=db_employee.tags,
        created_at=db_employee.created_at.isoformat() if db_employee.created_at else ""
    )

@router.delete("/{employee_id}")
async def delete_employee(
    employee_id: int,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user)
):
    result = await db.execute(select(Employee).where(Employee.id == employee_id))
    employee = result.scalar_one_or_none()
    if not employee:
        raise HTTPException(status_code=404, detail="Employee not found")
    
    await db.delete(employee)
    await db.commit()
    return {"ok": True}

@router.put("/{employee_id}", response_model=EmployeeResponse)
async def update_employee(
    employee_id: int,
    employee_update: EmployeeUpdate,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user)
):
    result = await db.execute(select(Employee).where(Employee.id == employee_id))
    db_employee = result.scalar_one_or_none()
    if not db_employee:
        raise HTTPException(status_code=404, detail="Employee not found")

    for key, value in employee_update.dict().items():
        setattr(db_employee, key, value)

    await db.commit()
    await db.refresh(db_employee)
    
    return EmployeeResponse(
        id=db_employee.id,
        full_name=db_employee.full_name,
        position=db_employee.position,
        phone=db_employee.phone,
        telegram_id=db_employee.telegram_id,
        department=db_employee.department,
        tags=db_employee.tags,
        created_at=db_employee.created_at.isoformat() if db_employee.created_at else ""
    )
