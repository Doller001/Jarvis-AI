"""
Main FastAPI Entrypoint for Jarvis AI Assistant Backend.
"""

import logging
import time
import uuid

from dotenv import load_dotenv

# Load environment variables from .env file
load_dotenv()

from fastapi import FastAPI, Request, Response
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from app.api.auth_routes import auth_router
from app.api.openai_compat import openai_router
from app.api.providers_api import providers_router
from app.api.routes import api_router
from app.realtime.ws import ws_router
from app.security.auth import get_allowed_origins
from app.security.exceptions import JarvisBaseException

logger = logging.getLogger("jarvis")
logging.basicConfig(level=logging.INFO)

app = FastAPI(
    title="Jarvis AI Backend",
    description="Low-Latency AI Assistant Cloud Brain & Multi-Provider Gateway",
    version="1.0.0"
)

# CORS Configuration
app.add_middleware(
    CORSMiddleware,
    allow_origins=get_allowed_origins(),
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.middleware("http")
async def add_correlation_id_and_timing(request: Request, call_next):
    request_id = request.headers.get("X-Request-ID", str(uuid.uuid4()))
    start_time = time.time()

    response: Response = await call_next(request)

    duration_ms = round((time.time() - start_time) * 1000, 2)
    response.headers["X-Request-ID"] = request_id
    response.headers["X-Response-Time-Ms"] = str(duration_ms)

    logger.info(f"[{request_id}] {request.method} {request.url.path} -> {response.status_code} ({duration_ms}ms)")
    return response


# Include Routers
app.include_router(auth_router)
app.include_router(ws_router)
app.include_router(providers_router)
app.include_router(openai_router)
app.include_router(api_router)

# Mount Desktop WebApp Static Directory
import os

from fastapi.responses import HTMLResponse, RedirectResponse
from fastapi.staticfiles import StaticFiles

webapp_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "webapp"))
if os.path.exists(webapp_dir):
    webapp_index = os.path.join(webapp_dir, "index.html")

    @app.get("/webapp/", include_in_schema=False)
    async def webapp_index_page():
        # Return the small shell directly.  This avoids the HTML-directory
        # fallback in Starlette StaticFiles, which can leave ASGI test clients
        # waiting indefinitely while resolving a directory URL.
        with open(webapp_index, encoding="utf-8") as index_file:
            return HTMLResponse(content=index_file.read())

    app.mount("/webapp", StaticFiles(directory=webapp_dir), name="webapp")

@app.get("/", tags=["System"])
@app.head("/", tags=["System"])
async def root_redirect():
    if os.path.exists(webapp_dir):
        return RedirectResponse(url="/webapp/")
    return {"status": "healthy", "service": "jarvis-backend", "version": "1.0.0"}


@app.get("/health", tags=["System"])
@app.head("/health", tags=["System"])
async def root_health():
    return {"status": "healthy", "service": "jarvis-backend", "version": "1.0.0"}


@app.get("/health/live", tags=["System"])
@app.head("/health/live", tags=["System"])
async def health_live():
    return {"status": "alive", "service": "jarvis-backend", "version": "1.0.0"}


@app.get("/health/ready", tags=["System"])
@app.head("/health/ready", tags=["System"])
async def health_ready():
    return {"status": "ready", "service": "jarvis-backend", "version": "1.0.0"}


@app.get("/api/v1/health", tags=["System"])
@app.head("/api/v1/health", tags=["System"])
async def api_v1_health():
    return {
        "status": "healthy",
        "service": "jarvis-backend",
        "api_version": "v1",
        "timestamp_ms": int(time.time() * 1000)
    }


@app.exception_handler(JarvisBaseException)
async def jarvis_exception_handler(request: Request, exc: JarvisBaseException):
    request_id = request.headers.get("X-Request-ID", "unknown")
    return JSONResponse(
        status_code=400,
        content={"error": exc.code, "message": exc.message, "request_id": request_id}
    )


@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    request_id = request.headers.get("X-Request-ID", "unknown")
    logger.error(f"[{request_id}] Unhandled exception: {exc}", exc_info=True)
    return JSONResponse(
        status_code=500,
        content={"error": "INTERNAL_SERVER_ERROR", "message": "Internal error.", "request_id": request_id}
    )
