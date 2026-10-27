import os
import sys
from pathlib import Path

import pytest

os.environ.setdefault("DJANGO_SETTINGS_MODULE", "aplicafacil_mcp.settings")
sys.path.insert(0, str(Path(__file__).resolve().parent))

from fakes import FakeSource, FakeUpstream, RecordingPublisher

REPO_ROOT = Path(__file__).resolve().parents[3]
SCHEMAS_DIR = REPO_ROOT / "contracts" / "schemas"


@pytest.fixture
def upstream() -> FakeUpstream:
    return FakeUpstream()


@pytest.fixture
def source() -> FakeSource:
    return FakeSource()


@pytest.fixture
def publisher() -> RecordingPublisher:
    return RecordingPublisher()
