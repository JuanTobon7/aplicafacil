"""Limpia textos que pueden terminar en logs, eventos o en la pantalla: sin keys, tokens ni headers."""

from __future__ import annotations

import re

_PATTERNS = [
    # API keys típicas: sk-..., sk-or-v1-..., sk-proj-..., incluso parcialmente enmascaradas (sk-abc***xyz)
    re.compile(r"\bsk-[A-Za-z0-9_\-\*\.]{4,}"),
    re.compile(r"(?i)\bbearer\s+[A-Za-z0-9_\-\.=~+/]+"),
    re.compile(r"(?i)\b(api[_-]?key|authorization|x-api-key|token|secret|password)\b(\"?\s*[:=]\s*\"?)[^\s\",}]+"),
    # JWT
    re.compile(r"\beyJ[A-Za-z0-9_\-]+\.[A-Za-z0-9_\-]+\.[A-Za-z0-9_\-]+"),
    # Cadenas largas con aspecto de secreto
    re.compile(r"\b[A-Za-z0-9_\-]{40,}\b"),
]

MAX_LENGTH = 300


def sanitize(text: str | None, *secrets: str, max_length: int = MAX_LENGTH) -> str | None:
    if text is None:
        return None
    clean = str(text)
    for secret in secrets:
        if secret:
            clean = clean.replace(secret, "[REDACTED]")
    for pattern in _PATTERNS:
        if pattern.groups >= 2:
            clean = pattern.sub(lambda m: f"{m.group(1)}{m.group(2)}[REDACTED]", clean)
        else:
            clean = pattern.sub("[REDACTED]", clean)
    clean = " ".join(clean.split())
    return clean if len(clean) <= max_length else clean[: max_length - 1] + "…"
