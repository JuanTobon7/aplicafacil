"""Logs estructurados (JSON) con correlationId y redacción de secretos.

``RedactingFilter`` pasa el mensaje y cada campo ``extra`` por ``sanitize``: aunque un error de una librería
incluya una key o un header ``Authorization``, no llega al log.
"""

from __future__ import annotations

import contextvars
import json
import logging
import sys
from datetime import UTC, datetime

from aplicafacil_mcp.domain.sanitize import sanitize

correlation_id_var: contextvars.ContextVar[str] = contextvars.ContextVar("correlation_id", default="-")

_RESERVED = set(logging.LogRecord("", 0, "", 0, "", (), None).__dict__) | {"message", "asctime"}


class RedactingFilter(logging.Filter):
    def filter(self, record: logging.LogRecord) -> bool:
        record.msg = sanitize(record.getMessage(), max_length=2000)
        record.args = None
        for key, value in list(record.__dict__.items()):
            if key not in _RESERVED and isinstance(value, str):
                setattr(record, key, sanitize(value, max_length=500))
        if record.exc_info and record.exc_info[1] is not None:
            record.exc_text = sanitize(logging.Formatter().formatException(record.exc_info), max_length=8000)
            record.exc_info = None
        record.correlation_id = correlation_id_var.get()
        return True


class JsonFormatter(logging.Formatter):
    def format(self, record: logging.LogRecord) -> str:
        entry = {
            "ts": datetime.fromtimestamp(record.created, UTC).isoformat(),
            "level": record.levelname,
            "logger": record.name,
            "message": record.getMessage(),
            "correlation_id": getattr(record, "correlation_id", "-"),
            "service": "mcp-gateway",
        }
        for key, value in record.__dict__.items():
            if key not in _RESERVED and key not in entry and not key.startswith("_"):
                entry[key] = value
        if record.exc_text:
            entry["exception"] = record.exc_text
        return json.dumps(entry, default=str, ensure_ascii=False)


def configure_logging(level: str = "INFO", fmt: str = "json") -> None:
    handler = logging.StreamHandler(sys.stdout)
    handler.addFilter(RedactingFilter())
    handler.setFormatter(
        JsonFormatter()
        if fmt == "json"
        else logging.Formatter("%(asctime)s %(levelname)s [%(correlation_id)s] %(name)s: %(message)s")
    )
    root = logging.getLogger()
    root.handlers = [handler]
    root.setLevel(level)
    # httpx registra cada URL a nivel INFO; las consultas llevan userId
    logging.getLogger("httpx").setLevel(logging.WARNING)
    logging.getLogger("aio_pika").setLevel(logging.WARNING)
    logging.getLogger("aiormq").setLevel(logging.WARNING)
