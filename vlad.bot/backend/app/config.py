from pydantic_settings import BaseSettings
from pydantic import PostgresDsn, SecretStr
from functools import lru_cache

class Settings(BaseSettings):
    DATABASE_URL: str
    SECRET_KEY: SecretStr
    
    # Telegram
    TELEGRAM_BOT_TOKEN: SecretStr | None = None
    
    # WhatsApp
    WHATSAPP_PHONE_ID: str | None = None
    WHATSAPP_ACCESS_TOKEN: SecretStr | None = None
    WHATSAPP_VERIFY_TOKEN: SecretStr | None = None

    class Config:
        env_file = ".env"

@lru_cache
def get_settings():
    return Settings()
