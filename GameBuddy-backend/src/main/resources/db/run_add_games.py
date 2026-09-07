#!/usr/bin/env python3
"""Runs add_popular_games.py with the workspace .env already loaded."""

import runpy
import sys
from pathlib import Path

import _env

_env.load()

# runpy rather than a subprocess: the child inherits this environment without the values
# ever reaching a command line, where they would land in shell history and in `ps`.
sys.argv = ["add_popular_games.py", *sys.argv[1:]]
runpy.run_path(str(Path(__file__).with_name("add_popular_games.py")), run_name="__main__")
