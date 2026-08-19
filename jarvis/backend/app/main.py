"""
Main FastAPI Entrypoint for Jarvis AI Assistant Backend.
"""

import uuid
import time
import logging
from fastapi import FastAPI, Request, Response
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from app.security.auth import get_allowed_origins
from app.security.exceptions import JarvisBaseException
from app.realtime.ws import ws_router
from app.api.providers_api import providers_router
from app.api.routes import api_router

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
app.include_router(ws_router)
app.include_router(providers_router)
app.include_router(api_router)


@app.get("/health", tags=["System"])
async def root_health():
    return {"status": "healthy", "service": "jarvis-backend", "version": "1.0.0"}


@app.get("/api/v1/health", tags=["System"])
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
