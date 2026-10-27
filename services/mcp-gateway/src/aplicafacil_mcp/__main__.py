"""``python -m aplicafacil_mcp``: arranca uvicorn con la configuración del entorno."""

import uvicorn

from aplicafacil_mcp.config import Settings


def main() -> None:
    settings = Settings.from_env()
    uvicorn.run(
        "aplicafacil_mcp.asgi:application",
        host=settings.host,
        port=settings.port,
        lifespan="on",
        log_config=None,  # logging_setup ya configura JSON + redacción
        proxy_headers=True,
        # Apagado ordenado: tiempo para terminar lo que está en curso antes de cortar
        timeout_graceful_shutdown=30,
    )


if __name__ == "__main__":
    main()
