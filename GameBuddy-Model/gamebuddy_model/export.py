"""Reads a live database into the CSVs the trainer already consumes.

Why this file has to exist
--------------------------
Training read CSVs and nothing else, and the only thing that wrote those CSVs was the
synthetic generator. There was no path from Postgres into training at all, which has a
consequence that is easy to miss: ``Recommender.similar_to`` can only ever return ids
that are *in the artefact*, so whoever the artefact was trained on is the entire
candidate universe. An artefact trained on synthetic gamers cannot recommend a real one,
no matter how many sign up. New accounts get served through the cold-start path, which
ranks them against the pool — it does not add them to it.

So this is the piece that makes the pipeline a loop rather than a one-shot:

    python -m gamebuddy_model export --out ./data      # live gamers → CSV
    python -m gamebuddy_model train  --data ./data     # CSV → artefact
    curl -X POST .../admin/reload                      # artefact → serving

Run it on a schedule and the model tracks the population. The synthetic generator keeps
its job — it is how the pipeline is tuned and tested offline, where the latent state is
known and the answers can be checked — but it stops being the only source of gamers.

Bot accounts are excluded by default. If a synthetic population was seeded into a local
or staging database to make the app demoable, training on it would teach the model about
profiles nobody is behind.
"""

from __future__ import annotations

import csv
from pathlib import Path
from typing import Any, Iterable

#: Rows are streamed from the server in batches rather than materialised. A year of
#: impressions from a live product does not want to be a Python list.
BATCH = 10_000

#: Synthetic accounts carry this domain (see ``seed.py``). Training on them would model
#: profiles that will never answer a message.
BOT_EMAIL_SUFFIX = "@bot.gamebuddy.invalid"

GAMERS_SQL = """
SELECT g.user_id, g.username, g.email, g.age, g.country, g.gender,
       COALESCE(EXTRACT(EPOCH FROM (NOW() - g.last_active_at)) / 60, -1)::bigint
         AS last_active_minutes_ago,
       COALESCE((SELECT string_agg(p.platform, '|' ORDER BY p.platform)
                 FROM {schema}.gamer_platform p WHERE p.user_id = g.user_id), '')
         AS platforms
  FROM {schema}.gamer g
 WHERE g.deleted_at IS NULL
   AND g.is_blocked = FALSE
   AND g.role <> 'ADMIN'
   {bot_filter}
"""

PROFILES_SQL = """
SELECT j.gamer_id, 'game' AS kind, ga.game_name AS value
  FROM {schema}.gamer_games_join j
  JOIN {schema}.games ga ON ga.game_id = j.game_id
  JOIN {schema}.gamer g ON g.user_id = j.gamer_id
 WHERE g.deleted_at IS NULL AND g.is_blocked = FALSE {bot_filter}
UNION ALL
SELECT j.gamer_id, 'keyword' AS kind, k.keyword_name AS value
  FROM {schema}.gamer_keywords_join j
  JOIN {schema}.keywords k ON k.id = j.keyword_id
  JOIN {schema}.gamer g ON g.user_id = j.gamer_id
 WHERE g.deleted_at IS NULL AND g.is_blocked = FALSE {bot_filter}
"""

#: One row per impression, labelled with what the viewer decided. A LIKE is an
#: ``approved_matches`` row in that direction; everything else the gamer was shown and
#: acted on is a PASS. Impressions with no decision yet are simply absent, which is
#: correct: they are not evidence either way.
INTERACTIONS_SQL = """
SELECT i.user_id, i.candidate_id,
       CASE WHEN a.user_id IS NOT NULL THEN 'LIKE' ELSE 'PASS' END AS decision
  FROM {schema}.recommendation_impression i
  LEFT JOIN {schema}.approved_matches a
         ON a.user_id = i.user_id AND a.matched_id = i.candidate_id
  LEFT JOIN {schema}.declined_matches d
         ON d.user_id = i.user_id AND d.declined_id = i.candidate_id
 WHERE a.user_id IS NOT NULL OR d.user_id IS NOT NULL
"""

#: A mutual match: the pair exists in ``approved_matches`` both ways round. Emitted once
#: per pair, in a stable order.
MATCHES_SQL = """
SELECT a.user_id, a.matched_id
  FROM {schema}.approved_matches a
  JOIN {schema}.approved_matches b
    ON b.user_id = a.matched_id AND b.matched_id = a.user_id
 WHERE a.user_id < a.matched_id
"""


def _stream(connection: Any, sql: str) -> Iterable[tuple]:
    """Yields rows in batches, so a large table never lands in memory at once."""
    with connection.cursor() as cursor:
        cursor.execute(sql)
        while rows := cursor.fetchmany(BATCH):
            yield from rows


def export(
    connection: Any,
    directory: Path | str,
    *,
    schema: str = "gamebuddy",
    include_bots: bool = False,
) -> dict[str, Path]:
    """Writes ``gamers``, ``profiles``, ``interactions`` and ``matches`` CSVs.

    Takes an already-open DB-API connection rather than a URL, so the caller owns the
    credentials and the test suite can hand it a stub. The four files are exactly the
    ones ``write_csv`` produces, so ``train`` cannot tell the difference between a real
    export and a synthetic one — which is the property that makes the offline tuning
    transfer.
    """
    directory = Path(directory)
    directory.mkdir(parents=True, exist_ok=True)

    bot_filter = "" if include_bots else f"AND g.email NOT LIKE '%{BOT_EMAIL_SUFFIX}'"
    fmt = {"schema": schema, "bot_filter": bot_filter}
    paths: dict[str, Path] = {}

    def dump(name: str, header: list[str], sql: str) -> None:
        paths[name] = path = directory / f"{name}.csv"
        with path.open("w", newline="", encoding="utf-8") as fh:
            writer = csv.writer(fh)
            writer.writerow(header)
            writer.writerows(_stream(connection, sql.format(**fmt)))

    dump("gamers",
         ["user_id", "username", "email", "age", "country", "gender",
          "last_active_minutes_ago", "platforms"],
         GAMERS_SQL)
    dump("profiles", ["user_id", "kind", "value"], PROFILES_SQL)
    dump("interactions", ["user_id", "target_id", "decision"], INTERACTIONS_SQL)
    dump("matches", ["user_id", "matched_id"], MATCHES_SQL)
    return paths


def connect(database_url: str) -> Any:
    """Opens a connection, with the import kept local.

    ``psycopg`` is only needed by this command, and the serving container has no reason
    to carry a database driver — the API reads a pickle and never talks to Postgres. A
    module-level import would make the whole package unimportable without it.
    """
    try:
        import psycopg
    except ImportError as exc:  # pragma: no cover - depends on the install
        raise SystemExit(
            "psycopg is required for `export`. Install it with:\n"
            "    pip install 'psycopg[binary]>=3.2,<4.0'"
        ) from exc
    return psycopg.connect(database_url)
