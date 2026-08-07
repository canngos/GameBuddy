#!/usr/bin/env python3
"""Runs backfill_game_covers.py with the workspace .env already loaded.

The backfill script reads plain environment variables so it can run anywhere — a CI job,
a deployment shell, a container. This wrapper exists for the ordinary case of running it
on a development machine, where all of those values are already sitting in the
workspace's gitignored .env and typing eight exports is a way to get one of them wrong.

It also assembles DATABASE_URL, which .env does not carry: docker-compose builds the
connection from DB_NAME, DB_USER, DB_PASSWORD and DB_PORT separately, and duplicating a
password into a second variable is how the two drift apart.

    python run_backfill.py              # dry run
    python run_backfill.py --apply
"""

from __future__ import annotations

import os
import runpy
import sys
from pathlib import Path

WORKSPACE = Path(__file__).resolve().parents[5]
ENV_FILE = WORKSPACE / ".env"


def load_env(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip().strip('"').strip("'")
    return values


def main() -> int:
    if not ENV_FILE.exists():
        print(f"No .env at {ENV_FILE}", file=sys.stderr)
        return 1

    values = load_env(ENV_FILE)
    os.environ.update(values)

    # Built here rather than stored, so the password lives in exactly one place.
    if "DATABASE_URL" not in os.environ:
        missing = [k for k in ("DB_USER", "DB_PASSWORD", "DB_NAME") if not values.get(k)]
        if missing:
            print(f"Cannot build DATABASE_URL, missing: {', '.join(missing)}", file=sys.stderr)
            return 1
        port = values.get("DB_PORT", "5432")
        os.environ["DATABASE_URL"] = (
            f"postgresql://{values['DB_USER']}:{values['DB_PASSWORD']}"
            f"@localhost:{port}/{values['DB_NAME']}"
        )

    print(f"Loaded {len(values)} values from {ENV_FILE.name}.\n")

    # runpy rather than a subprocess: the child inherits this process's environment
    # without the values ever appearing on a command line, where they would show up in
    # the shell history and in `ps`.
    sys.argv = ["backfill_game_covers.py", *sys.argv[1:]]
    runpy.run_path(str(Path(__file__).with_name("backfill_game_covers.py")), run_name="__main__")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
