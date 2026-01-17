import httpx
from app.config import get_settings
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from app.models import SystemSetting
import logging

logger = logging.getLogger(__name__)

class MessagingService:
    @staticmethod
    async def get_setting_value(db: AsyncSession, key: str, default: str = None) -> str | None:
        try:
            result = await db.execute(select(SystemSetting).where(SystemSetting.key == key))
            setting = result.scalar_one_or_none()
            if setting and setting.value:
                return setting.value
        except Exception as e:
            logger.error(f"Error fetching setting {key}: {e}")
        return default

    @staticmethod
    async def send_telegram(db: AsyncSession, chat_id: str, text: str, token: str = None):
        """
        Sends message via Telegram Bot API.
        """
        # 1. Try passed token
        bot_token = token
        
        # 2. Try DB Setting
        if not bot_token:
            bot_token = await MessagingService.get_setting_value(db, "telegram_bot_token")
            
        # 3. Try Env
        if not bot_token:
            bot_token = get_settings().TELEGRAM_BOT_TOKEN
        
        if not bot_token:
            logger.warning("No Telegram Token found (DB or Env). Skipping message.")
            return False

        url = f"https://api.telegram.org/bot{bot_token}/sendMessage"
        payload = {
            "chat_id": chat_id,
            "text": text,
            "parse_mode": "HTML"
        }
        
        async with httpx.AsyncClient() as client:
            try:
                resp = await client.post(url, json=payload, timeout=10.0)
                if resp.status_code != 200:
                    logger.error(f"Telegram API Error: {resp.text}")
                resp.raise_for_status()
                return True
            except Exception as e:
                logger.error(f"Telegram Send Error: {e}")
                return False

    @staticmethod
    async def send_whatsapp(db: AsyncSession, phone: str, text: str):
        """
        Sends message via WhatsApp.
        """
        # Fetch Settings
        wa_url = await MessagingService.get_setting_value(db, "whatsapp_api_url") or get_settings().WA_API_URL
        wa_instance = await MessagingService.get_setting_value(db, "whatsapp_instance_id") or get_settings().WA_INSTANCE_ID
        wa_token = await MessagingService.get_setting_value(db, "whatsapp_access_token") or get_settings().WA_ACCESS_TOKEN

        if not wa_url:
             logger.warning("WhatsApp API URL not configured. Skipping.")
             return False
        
        # Construct URL (handle trailing slash)
        base_url = wa_url.rstrip('/')
        # GreenAPI style: /waInstance{id}/sendMessage/{token}
        # Or generic webhook style. 
        # Let's assume generic or GreenAPI style provided by user config.
        # User said "GreenAPI".
        # GreenAPI URL: https://api.green-api.com
        # format: {host}/waInstance{id}/sendMessage/{token}
        
        # If user puts full URL in settings, use it.
        # Simplified:
        api_url = f"{base_url}/waInstance{wa_instance}/sendMessage/{wa_token}"
        
        payload = {
            "chatId": f"{phone}@c.us",
            "message": text
        }
        
        async with httpx.AsyncClient() as client:
            try:
                resp = await client.post(api_url, json=payload)
                if resp.status_code != 200:
                    logger.error(f"WhatsApp API Error: {resp.text}")
                resp.raise_for_status()
                return True
            except Exception as e:
                logger.error(f"WhatsApp Send Error: {e}")
                return False
