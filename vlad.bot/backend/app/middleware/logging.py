import logging
import time
from fastapi import Request
from starlette.middleware.base import BaseHTTPMiddleware

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

class LoggingMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next):
        start_time = time.time()
        
        # Log request
        logger.info(f"→ {request.method} {request.url.path} | Client: {request.client.host}")
        
        try:
            response = await call_next(request)
            process_time = time.time() - start_time
            
            # Log response
            logger.info(
                f"← {request.method} {request.url.path} | "
                f"Status: {response.status_code} | "
                f"Time: {process_time:.3f}s"
            )
            
            response.headers["X-Process-Time"] = str(process_time)
            return response
            
        except Exception as exc:
            process_time = time.time() - start_time
            logger.error(
                f"✗ {request.method} {request.url.path} | "
                f"Error: {str(exc)} | "
                f"Time: {process_time:.3f}s",
                exc_info=True
            )
            raise
