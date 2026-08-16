"""Tests for the Postgres export, against a stub connection.

No database is involved. The queries are checked for the properties that would silently
produce a wrong training set — excluding bots, excluding deleted accounts, emitting each
mutual pair once — and the writer is checked against a canned result set. Whether the SQL
is *valid* is a question for the first real run; whether it asks for the right thing is a
question for here, and that is the half that fails quietly.
"""

from __future__ import annotations

import csv

import pytest

from gamebuddy_model.export import (BOT_EMAIL_SUFFIX, GAMERS_SQL, INTERACTIONS_SQL,
                                    MATCHES_SQL, PROFILES_SQL, export)


class StubCursor:
    def __init__(self, responses: dict[str, list[tuple]], executed: list[str]) -> None:
        self._responses = responses
        self._executed = executed
        self._rows: list[tuple] = []

    def __enter__(self) -> "StubCursor":
        return self

    def __exit__(self, *exc) -> None:
        return None

    def execute(self, sql: str) -> None:
        self._executed.append(sql)
        for marker, rows in self._responses.items():
            if marker in sql:
                self._rows = list(rows)
                return
        self._rows = []

    def fetchmany(self, size: int) -> list[tuple]:
        batch, self._rows = self._rows[:size], self._rows[size:]
        return batch


class StubConnection:
    """Just enough DB-API to satisfy ``_stream``."""

    def __init__(self, responses: dict[str, list[tuple]]) -> None:
        self.responses = responses
        self.executed: list[str] = []

    def cursor(self) -> StubCursor:
        return StubCursor(self.responses, self.executed)


@pytest.fixture
def connection() -> StubConnection:
    return StubConnection({
        "FROM {schema}.gamer g".format(schema="gamebuddy"): [
            ("u1", "ada", "ada@example.com", 24, "Turkey", "F", 12, "PC|SWITCH"),
            ("u2", "linus", "linus@example.com", 31, "Finland", "M", 900, "PC"),
        ],
        "gamer_games_join": [
            ("u1", "game", "VALORANT"),
            ("u1", "keyword", "chill"),
            ("u2", "game", "Factorio"),
        ],
        "recommendation_impression": [("u1", "u2", "LIKE"), ("u2", "u1", "LIKE")],
        "JOIN {schema}.approved_matches b".format(schema="gamebuddy"): [("u1", "u2")],
    })


def _read(path):
    with path.open(encoding="utf-8") as fh:
        return list(csv.DictReader(fh))


def test_export_writes_the_four_files_train_expects(connection, tmp_path):
    paths = export(connection, tmp_path)
    assert set(paths) == {"gamers", "profiles", "interactions", "matches"}
    for path in paths.values():
        assert path.exists()


def test_exported_csvs_match_the_trainers_schema(connection, tmp_path):
    """``train`` reads these by column name, so a renamed column is a silent failure —
    it trains on an empty profile set rather than raising."""
    paths = export(connection, tmp_path)

    gamers = _read(paths["gamers"])
    assert [g["user_id"] for g in gamers] == ["u1", "u2"]
    assert gamers[0]["platforms"] == "PC|SWITCH"

    profiles = _read(paths["profiles"])
    assert {p["kind"] for p in profiles} == {"game", "keyword"}
    assert set(profiles[0]) == {"user_id", "kind", "value"}

    interactions = _read(paths["interactions"])
    assert set(interactions[0]) == {"user_id", "target_id", "decision"}
    assert {i["decision"] for i in interactions} <= {"LIKE", "PASS"}


def test_bot_accounts_are_excluded_by_default(connection, tmp_path):
    """Synthetic seed accounts in a staging database must not become training data —
    the model would learn about profiles nobody is behind."""
    export(connection, tmp_path)
    gamer_queries = [q for q in connection.executed if "FROM gamebuddy.gamer g" in q]
    assert gamer_queries
    assert all(f"NOT LIKE '%{BOT_EMAIL_SUFFIX}'" in q for q in gamer_queries)


def test_bots_can_be_included_deliberately(connection, tmp_path):
    export(connection, tmp_path, include_bots=True)
    assert all(BOT_EMAIL_SUFFIX not in q for q in connection.executed)


def test_deleted_and_blocked_accounts_are_never_exported():
    """A deleted account that stays in the artefact keeps being recommended, and the
    backend then filters it out of every feed it appears in — a silently shorter deck."""
    assert "deleted_at IS NULL" in GAMERS_SQL
    assert "is_blocked = FALSE" in GAMERS_SQL
    assert "deleted_at IS NULL" in PROFILES_SQL


def test_admins_are_not_exported():
    """Staff accounts are not discoverable in the product, so they should not be in the
    candidate pool the model ranks over."""
    assert "role <> 'ADMIN'" in GAMERS_SQL


def test_mutual_matches_are_emitted_once_per_pair():
    """``approved_matches`` stores both directions. Without the ordering predicate every
    match would be exported twice and weighted double."""
    assert "a.user_id < a.matched_id" in MATCHES_SQL


def test_undecided_impressions_are_not_labelled():
    """An impression the gamer never acted on is not a pass. Counting it as one would
    tell the desirability prior that everyone shown to a lapsed user was rejected."""
    assert "a.user_id IS NOT NULL OR d.user_id IS NOT NULL" in INTERACTIONS_SQL


def test_schema_is_parameterised(connection, tmp_path):
    export(connection, tmp_path, schema="schtrain")
    assert any("schtrain.gamer" in q for q in connection.executed)
    assert all("gamebuddy.gamer " not in q for q in connection.executed)
