"""``TokenVerifier`` del SDK MCP para los JWT de identity-service (RS256, JWKS).

Acepta solo tokens de **usuario** (``roles`` contiene ``USER``) emitidos por ``AUTH_ISSUER_URI`` para
``aud = aplicafacil-api``. El usuario de cada tool es el ``sub``; nunca un argumento.
"""

from __future__ import annotations

import asyncio
import logging

import jwt
from mcp.server.auth.provider import AccessToken

log = logging.getLogger(__name__)


class IdentityTokenVerifier:
    def __init__(self, jwks_uri: str, issuer: str, audience: str, *, jwks_client: jwt.PyJWKClient | None = None):
        self._issuer = issuer
        self._audience = audience
        # Cachea las llaves y vuelve a pedirlas si llega un kid desconocido (rotación)
        self._jwks = jwks_client or jwt.PyJWKClient(jwks_uri, cache_keys=True, lifespan=300, timeout=5)

    async def verify_token(self, token: str) -> AccessToken | None:
        try:
            # PyJWKClient es bloqueante (urllib): fuera del event loop
            signing_key = await asyncio.to_thread(self._jwks.get_signing_key_from_jwt, token)
            claims = jwt.decode(
                token,
                signing_key.key,
                algorithms=["RS256"],
                audience=self._audience,
                issuer=self._issuer,
                options={"require": ["exp", "iat", "sub", "iss", "aud"]},
                leeway=30,
            )
        except (jwt.PyJWTError, jwt.PyJWKClientError) as e:
            log.info("Token rechazado", extra={"reason": type(e).__name__})
            return None
        roles = claims.get("roles") or []
        if "USER" not in roles:
            log.info("Token rechazado", extra={"reason": "not_a_user_token"})
            return None
        return AccessToken(
            token=token,
            client_id=str(claims["sub"]),
            subject=str(claims["sub"]),
            scopes=[f"ROLE_{r}" for r in roles],
            expires_at=int(claims["exp"]),
            resource=None,
            claims={"iss": claims["iss"], "roles": roles},
        )
