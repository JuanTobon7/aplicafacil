#!/usr/bin/env python
"""Utilidades de Django (``check``, ``shell``…). Para correr el servicio: ``python -m aplicafacil_mcp``."""

import os
import sys
from pathlib import Path

if __name__ == "__main__":
    sys.path.insert(0, str(Path(__file__).resolve().parent / "src"))
    os.environ.setdefault("DJANGO_SETTINGS_MODULE", "aplicafacil_mcp.settings")
    from django.core.management import execute_from_command_line

    execute_from_command_line(sys.argv)
