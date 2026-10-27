# mcp-gateway (Python 3.13 + uv). docker build -t aplicafacil/mcp-gateway services/mcp-gateway
FROM python:3.13-slim AS build
COPY --from=ghcr.io/astral-sh/uv:0.13.1 /uv /usr/local/bin/uv
WORKDIR /app
ENV UV_COMPILE_BYTECODE=1 UV_LINK_MODE=copy UV_PYTHON_DOWNLOADS=never
# Primero las dependencias (capa cacheable), luego el código
COPY pyproject.toml uv.lock ./
RUN --mount=type=cache,target=/root/.cache/uv uv sync --frozen --no-dev --no-install-project
COPY src/ src/
COPY manage.py ./
RUN --mount=type=cache,target=/root/.cache/uv uv sync --frozen --no-dev

FROM python:3.13-slim
RUN useradd --system --uid 1001 mcp
WORKDIR /app
COPY --from=build --chown=mcp /app /app
USER mcp
ENV PATH="/app/.venv/bin:$PATH" PYTHONUNBUFFERED=1 MCP_HOST=0.0.0.0 MCP_PORT=8000
EXPOSE 8000
# uvicorn recibe SIGTERM y hace el apagado ordenado (baja de Eureka, deja de consumir, cierra adaptadores)
STOPSIGNAL SIGTERM
CMD ["python", "-m", "aplicafacil_mcp"]
