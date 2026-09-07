"""Loads the workspace .env for the one-off catalogue scripts.

Both scripts read plain environment variables so they can run anywhere — CI, a deployment
shell, a container. This is the convenience for a development machine, where those values
are already in the gitignored .env and typing eight exports is a way to get one wrong.
"""

from __future__ import annotations

import os
import sys
from pathlib import Path

WORKSPACE = Path(__file__).resolve().parents[5]
ENV_FILE = WORKSPACE / ".env"


def load() -> dict[str, str]:
    if not ENV_FILE.exists():
        print(f"No .env at {ENV_FILE}", file=sys.stderr)
        raise SystemExit(1)

    values: dict[str, str] = {}
    for raw in ENV_FILE.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip().strip('"').strip("'")

    os.environ.update(values)

    # Assembled rather than stored: docker-compose builds the connection from separate
    # parts, and duplicating a password into a second variable is how the two drift.
    if "DATABASE_URL" not in os.environ:
        absent = [k for k in ("DB_USER", "DB_PASSWORD", "DB_NAME") if not values.get(k)]
        if absent:
            print(f"Cannot build DATABASE_URL, missing: {', '.join(absent)}", file=sys.stderr)
            raise SystemExit(1)
        os.environ["DATABASE_URL"] = (
            f"postgresql://{values['DB_USER']}:{values['DB_PASSWORD']}"
            f"@localhost:{values.get('DB_PORT', '5432')}/{values['DB_NAME']}"
        )

    print(f"Loaded {len(values)} values from {ENV_FILE.name}.\n")
    return values
