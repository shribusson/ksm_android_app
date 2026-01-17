import io
import openpyxl
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from app.models import Session, Answer, Respondent

class ExportService:
    def __init__(self, db: AsyncSession):
        self.db = db
    
    async def generate_excel(self, bot_id: int):
        # 1. Fetch Sessions
        query = select(Session).join(Respondent).where(Respondent.bot_id == bot_id)
        result = await self.db.execute(query)
        sessions = result.scalars().all()
        
        # 2. Fetch Answers
        # Optimization: Fetch all answers for these sessions
        s_ids = [s.id for s in sessions]
        if not s_ids:
            return self._create_empty_excel()
            
        a_query = select(Answer).where(Answer.session_id.in_(s_ids))
        a_result = await self.db.execute(a_query)
        answers = a_result.scalars().all()
        
        # Optimize lookup: session_id -> {question_key: value}
        data_map = {}
        all_keys = set()
        
        for ans in answers:
            if ans.session_id not in data_map:
                data_map[ans.session_id] = {}
            data_map[ans.session_id][ans.question_key] = ans.value
            all_keys.add(ans.question_key)
            
        sorted_keys = sorted(list(all_keys))
        
        # 3. Build Excel
        wb = openpyxl.Workbook()
        ws = wb.active
        ws.title = "Results"
        
        # Headers
        headers = ["session_id", "state", "started_at", "finished_at"] + sorted_keys
        ws.append(headers)
        
        for s in sessions:
            row = [
                s.id,
                s.state,
                s.started_at.isoformat() if s.started_at else "",
                s.finished_at.isoformat() if s.finished_at else ""
            ]
            
            s_data = data_map.get(s.id, {})
            for k in sorted_keys:
                row.append(s_data.get(k, ""))
            
            ws.append(row)
            
        output = io.BytesIO()
        wb.save(output)
        output.seek(0)
        return output

    def _create_empty_excel(self):
        wb = openpyxl.Workbook()
        ws = wb.active
        ws.append(["No Data"])
        output = io.BytesIO()
        wb.save(output)
        output.seek(0)
        return output
