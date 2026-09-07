#!/usr/bin/env python3
"""Tag the games that predate game_platform with the platforms they run on.

``add_platform_games.py`` tags what it inserts. The hundred games that were in the
catalogue before upgrade-2026-29 existed have no rows at all, and a half-tagged catalogue is
worse than an untagged one: the picker would group half the list and drop the rest into
"everything else", which reads as a bug rather than as a missing feature.

**Matched by name, through the same machinery as the cover backfill.** ``resolve()`` in
``backfill_game_covers.py`` already carries the overrides and the similarity gate that stop
"The Finals" resolving to Final Fantasy, and it is imported rather than re-implemented so
the two cannot disagree about which IGDB entry a catalogue row means.

**Idempotent, twice over.** ``ON CONFLICT DO NOTHING`` on the (game_id, platform) key means
a repeat run writes nothing, and ``--only-untagged`` (the default) skips games that already
have rows so an interrupted run resumes cheaply instead of re-querying IGDB for all of them.

Run the dry run first. It prints what it would tag and writes nothing:

    python backfill_game_platforms.py             # dry run
    python backfill_game_platforms.py --apply
    python backfill_game_platforms.py --all       # re-check games already tagged

Needs IGDB_CLIENT_ID, IGDB_CLIENT_SECRET and DATABASE_URL. No R2 — nothing is uploaded.
Run it from this directory; it imports from its two siblings.
"""

from __future__ import annotations

import argparse
import os
import sys

import psycopg
import requests

from add_platform_games import (
    PLATFORM_FAMILIES,
    platform_lookup,
    resolve_platform_ids,
)
from add_popular_games import igdb_token
from backfill_game_covers import resolve

IGDB_GAMES_URL = "https://api.igdb.com/v4/games"


def platforms_of(igdb_id: int, headers: dict) -> list[int]:
    """The IGDB platform ids for one game.

    A second request per game rather than a field on the first: ``resolve()`` returns a
    Match, which deliberately carries only what the cover backfill needed. Widening that
    record would change what its own caller fetches for a hundred games it does not use.
    """
    response = requests.post(
        IGDB_GAMES_URL,
        headers=headers,
        data=f"fields platforms; where id = {igdb_id}; limit 1;",
        timeout=20,
    )
    response.raise_for_status()
    rows = response.json()
    return (rows[0].get("platforms") or []) if rows else []


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true", help="actually write")
    parser.add_argument(
        "--all",
        action="store_true",
        help="re-check games that already have platform rows",
    )
    args = parser.parse_args()

    missing = [
        name
        for name in ("IGDB_CLIENT_ID", "IGDB_CLIENT_SECRET", "DATABASE_URL")
        if not os.environ.get(name)
    ]
    if missing:
        print(f"Missing environment variables: {', '.join(missing)}", file=sys.stderr)
        return 1

    client_id = os.environ["IGDB_CLIENT_ID"]
    token = igdb_token(client_id, os.environ["IGDB_CLIENT_SECRET"])
    headers = {"Client-ID": client_id, "Authorization": f"Bearer {token}"}

    print("Resolving IGDB platform ids...")
    tag_for = platform_lookup(resolve_platform_ids(headers))

    with psycopg.connect(os.environ["DATABASE_URL"]) as connection:
        with connection.cursor() as cursor:
            if args.all:
                cursor.execute("SELECT game_id, game_name FROM gamebuddy.games ORDER BY game_name")
            else:
                cursor.execute(
                    """
                    SELECT g.game_id, g.game_name
                      FROM gamebuddy.games g
                     WHERE NOT EXISTS (
                           SELECT 1 FROM gamebuddy.game_platform p
                            WHERE p.game_id = g.game_id)
                     ORDER BY g.game_name
                    """
                )
            rows = cursor.fetchall()

        print(f"{len(rows)} game(s) to check.\n")

        tagged: list[tuple[str, str, list[str]]] = []
        unmatched: list[str] = []
        untaggable: list[str] = []

        for game_id, game_name in rows:
            match = resolve(game_name, client_id, token)
            if match is None:
                # Same outcome the cover backfill reports for these: IGDB cannot identify
                # the catalogue name confidently, and guessing is how a game ends up tagged
                # with another game's platforms.
                unmatched.append(game_name)
                continue

            tags = sorted(
                {tag_for[p] for p in platforms_of(match.igdb_id, headers) if p in tag_for}
            )
            if not tags:
                # Retro, mostly: Chrono Trigger, Street Fighter II, Super Mario 64 and
                # Ocarina of Time are SNES, arcade and N64 records. OTHER says that outright
                # instead of leaving a blank that reads as a lookup we never did.
                tags = ["OTHER"]
                untaggable.append(game_name)
            tagged.append((game_id, game_name, tags))
            print(f"  + {game_name[:44]:46} {','.join(tags)}")

        print(f"\n{len(tagged)} taggable, {len(unmatched)} unmatched, "
              f"{len(untaggable)} tagged OTHER.")
        for name in unmatched:
            print(f"  !  {name}: not identified on IGDB")
        for name in untaggable:
            print(f"  ~  {name}: runs on nothing the enum names, tagged OTHER")

        if not args.apply:
            print("\nDry run — nothing written. Re-run with --apply.")
            return 0

        with connection.cursor() as cursor:
            for game_id, _, tags in tagged:
                cursor.executemany(
                    """
                    INSERT INTO gamebuddy.game_platform (game_id, platform)
                    VALUES (%s, %s)
                    ON CONFLICT (game_id, platform) DO NOTHING
                    """,
                    [(game_id, tag) for tag in tags],
                )
        connection.commit()

        with connection.cursor() as cursor:
            cursor.execute(
                """
                SELECT platform, count(*)
                  FROM gamebuddy.game_platform
                 GROUP BY platform
                 ORDER BY count(*) DESC
                """
            )
            print("\nCatalogue now tagged:")
            for platform, count in cursor.fetchall():
                print(f"  {platform:12} {count:4}")

            cursor.execute(
                """
                SELECT count(*) FROM gamebuddy.games g
                 WHERE NOT EXISTS (SELECT 1 FROM gamebuddy.game_platform p
                                    WHERE p.game_id = g.game_id)
                """
            )
            print(f"  {'(untagged)':12} {cursor.fetchone()[0]:4}")

    # Stated rather than left to be noticed: PLATFORM_FAMILIES is the whole vocabulary, so a
    # platform absent from it is absent from every count above.
    print(f"\nPlatforms recognised: {', '.join(PLATFORM_FAMILIES)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
