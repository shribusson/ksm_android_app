from sqlalchemy import Column, Integer, String, Boolean, DateTime, CheckConstraint, ForeignKey
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.sql import func
from app.database import Base

class User(Base):
    __tablename__ = "users"
    
    id = Column(Integer, primary_key=True, index=True)
    username = Column(String, unique=True, index=True, nullable=False)
    password_hash = Column(String, nullable=False)
    is_active = Column(Boolean, default=True)
    created_at = Column(DateTime(timezone=True), server_default=func.now())

class SystemSetting(Base):
    __tablename__ = "system_settings"
    
    key = Column(String, primary_key=True, index=True)
    value = Column(String, nullable=False) # Store simple strings (tokens)
    updated_at = Column(DateTime(timezone=True), onupdate=func.now())

class Bot(Base):
    __tablename__ = "bots"
    
    id = Column(Integer, primary_key=True, index=True)
    name = Column(String, nullable=False)
    description = Column(String)
    status = Column(String, default="draft") # draft, active, archived
    trigger_keywords = Column(String, nullable=True) # Comma separated keywords: "start,onboarding"
    active_script_version = Column(Integer, nullable=True)
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    updated_at = Column(DateTime(timezone=True), onupdate=func.now())

class Script(Base):
    __tablename__ = "scripts"
    
    id = Column(Integer, primary_key=True, index=True)
    bot_id = Column(Integer, ForeignKey("bots.id", ondelete="CASCADE"), nullable=False)
    title = Column(String, nullable=True)
    version = Column(Integer, nullable=False)
    is_published = Column(Boolean, default=False)
    graph_data = Column(JSONB, nullable=False)
    created_at = Column(DateTime(timezone=True), server_default=func.now())

class ChannelConfig(Base):
    __tablename__ = "channel_configs"
    
    id = Column(Integer, primary_key=True, index=True)
    bot_id = Column(Integer, ForeignKey("bots.id", ondelete="CASCADE"), nullable=False)
    channel_type = Column(String, nullable=False)
    credentials = Column(JSONB, nullable=False)
    is_active = Column(Boolean, default=True)

class Respondent(Base):
    __tablename__ = "respondents"
    
    id = Column(Integer, primary_key=True, index=True)
    bot_id = Column(Integer, ForeignKey("bots.id", ondelete="CASCADE"), nullable=False)
    channel_type = Column(String, nullable=False)
    external_id = Column(String, nullable=False)
    profile_data = Column(JSONB, default={})
    created_at = Column(DateTime(timezone=True), server_default=func.now())

class Session(Base):
    __tablename__ = "sessions"
    
    id = Column(Integer, primary_key=True, index=True)
    bot_id = Column(Integer, ForeignKey("bots.id", ondelete="CASCADE"), nullable=False)
    respondent_id = Column(Integer, ForeignKey("respondents.id", ondelete="CASCADE"), nullable=False)
    script_id = Column(Integer, ForeignKey("scripts.id", ondelete="CASCADE"), nullable=False)
    state = Column(String, default="active")
    current_node_id = Column(String, nullable=True)
    variables = Column(JSONB, default={})
    started_at = Column(DateTime(timezone=True), server_default=func.now())
    finished_at = Column(DateTime(timezone=True), nullable=True)

class Answer(Base):
    __tablename__ = "answers"
    
    id = Column(Integer, primary_key=True, index=True)
    session_id = Column(Integer, ForeignKey("sessions.id", ondelete="CASCADE"), nullable=False)
    respondent_id = Column(Integer, ForeignKey("respondents.id", ondelete="CASCADE"), nullable=False)
    node_id = Column(String, nullable=True)
    question_key = Column(String, nullable=False)
    value = Column(String, nullable=True)
    created_at = Column(DateTime(timezone=True), server_default=func.now())

class Employee(Base):
    __tablename__ = "employees"
    
    id = Column(Integer, primary_key=True, index=True)
    full_name = Column(String, nullable=False)
    position = Column(String, nullable=True)
    phone = Column(String, nullable=True, index=True) # Format: 79991234567
    telegram_id = Column(String, nullable=True, index=True)
    department = Column(String, nullable=True)
    tags = Column(String, nullable=True) # Comma-separated tags: "onboarding,sales,vip"
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    updated_at = Column(DateTime(timezone=True), onupdate=func.now())
