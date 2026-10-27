"""Settings de Django. Django solo sirve health, readiness y métricas: sin base de datos, sesiones ni admin.

La configuración del resto del servicio está en ``aplicafacil_mcp.config`` (variables de entorno).
"""

import os
import secrets

DEBUG = os.environ.get("DJANGO_DEBUG", "false").lower() == "true"
# No hay sesiones ni formularios: la llave solo existe porque Django la exige
SECRET_KEY = os.environ.get("DJANGO_SECRET_KEY") or secrets.token_urlsafe(50)
ALLOWED_HOSTS = [h.strip() for h in os.environ.get("DJANGO_ALLOWED_HOSTS", "127.0.0.1,localhost,mcp-gateway").split(",")]

INSTALLED_APPS = ["aplicafacil_mcp.web"]
MIDDLEWARE = [
    "django.middleware.security.SecurityMiddleware",
    "django.middleware.common.CommonMiddleware",
]
ROOT_URLCONF = "aplicafacil_mcp.web.urls"
ASGI_APPLICATION = "aplicafacil_mcp.asgi.application"

# Sin persistencia local: la fuente de verdad es ai-config-service (ADR 0005)
DATABASES: dict = {}

USE_TZ = True
TIME_ZONE = "UTC"
APPEND_SLASH = False
SECURE_CONTENT_TYPE_NOSNIFF = True

LOGGING_CONFIG = None  # lo configura aplicafacil_mcp.logging_setup
