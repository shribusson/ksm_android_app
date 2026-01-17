from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from app.models import User
from app.auth import get_password_hash
import logging

logger = logging.getLogger(__name__)

async def create_default_user(db: AsyncSession):
    try:
        query = select(User).where(User.username == "admin")
        result = await db.execute(query)
        user = result.scalar_one_or_none()
        
        if not user:
            logger.info("Creating default admin user")
            # Password: admin
            pwd = get_password_hash("admin")
            user = User(username="admin", password_hash=pwd)
            db.add(user)
            await db.commit()
        else:
            logger.info("Default admin user exists")
    except Exception as e:
        logger.warning(f"Could not create default user (likely tables missing): {e}")
        await db.rollback()

