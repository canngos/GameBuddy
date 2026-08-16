#!/usr/bin/env python3
"""Re-derive the genre of games added before the categoriser learned the full vocabulary.

The catalogue speaks two dialects. The hand-typed seed used JRPG, MMO, Roguelike,
Metroidvania and Battle Royale; ``add_popular_games.categorise`` could produce none of them,
so the two hundred games added on 2026-08-16 were filed under the nearest broad genre — a
JRPG as RPG, Apex Legends as FPS, World of Warcraft as RPG.

That was invisible while genre was a label on a card. It stops being invisible the moment
genre becomes a filter, because tapping "JRPG" returns the eight seeded ones and none of the
newer ones, which reads as a broken feature rather than a thin catalogue.

``categorise`` now reads IGDB's ``keywords`` and ``game_modes`` as well, which is where
those five actually live. This applies the improved rules to rows already written.

**Only the games this script's siblings added, and the seed is identified by its UUID
version rather than by date.** The hand-typed hundred carry deterministic version-5 ids —
derived from the name, which is why their R2 cover keys matched across environments — while
both add-scripts mint version-4 ids with uuid.uuid4(). Exactly 200 v4 and 100 v5.

The first attempt filtered on ``created_at`` and swept all 300, because production was first
seeded on the same day the extra games were added, so every row carried the same date. The
dry run caught it: it proposed Ocarina of Time from Adventure to Puzzle, Terraria from
Sandbox to Horror, The Sims 4 from Simulation to RPG, and — the giveaway — it destroyed
three JRPG labels, the exact vocabulary this exists to preserve. Hand-typed categories are
usually better than anything derived, because "Dragon Quest XI S is a JRPG" is knowledge
IGDB does not encode.

``--all`` re-derives everything including the seed. It is almost never what you want.

Run the dry run first. It prints every change it would make and writes nothing:

    python recategorise_games.py            # dry run, script-added games only
    python recategorise_games.py --apply
    python recategorise_games.py --all      # including the hand-typed seed

Needs IGDB_CLIENT_ID, IGDB_CLIENT_SECRET and DATABASE_URL. No R2 — nothing is uploaded.
Run it from this directory; it imports from its siblings.
"""

from __future__ import annotations

import argparse
import collections
import os
import sys

import psycopg
import requests

from add_popular_games import (
    IGDB_GAMES_URL,
    REQUEST_INTERVAL_SECONDS,
    categorise,
    igdb_token,
    load_keyword_categories,
)
from backfill_game_covers import resolve

import time

# Everything categorise() reads. Kept in one place so a rule that starts consulting a new
# field fails loudly here rather than silently deciding on absent data.
FIELDS = "fields name,genres.id,genres.name,themes,game_modes,keywords;"


def details(igdb_id: int, headers: dict) -> dict | None:
    time.sleep(REQUEST_INTERVAL_SECONDS)
    response = requests.post(
        IGDB_GAMES_URL, headers=headers, data=f"{FIELDS} where id = {igdb_id}; limit 1;",
        timeout=20,
    )
    response.raise_for_status()
    rows = response.json()
    return rows[0] if rows else None


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true", help="actually write")
    parser.add_argument(
        "--all",
        action="store_true",
        help="re-derive every game, including the hand-categorised version-5 seed",
    )
    args = parser.parse_args()

    missing = [
        n for n in ("IGDB_CLIENT_ID", "IGDB_CLIENT_SECRET", "DATABASE_URL")
        if not os.environ.get(n)
    ]
    if missing:
        print(f"Missing environment variables: {', '.join(missing)}", file=sys.stderr)
        return 1

    client_id = os.environ["IGDB_CLIENT_ID"]
    token = igdb_token(client_id, os.environ["IGDB_CLIENT_SECRET"])
    headers = {"Client-ID": client_id, "Authorization": f"Bearer {token}"}

    print("Resolving IGDB keyword ids...")
    load_keyword_categories(headers)

    with psycopg.connect(os.environ["DATABASE_URL"]) as connection:
        with connection.cursor() as cursor:
            if args.all:
                cursor.execute(
                    "SELECT game_id, game_name, category FROM gamebuddy.games ORDER BY game_name"
                )
            else:
                # Character 15 of a UUID is its version. The seed is v5 and stays as typed;
                # everything the add-scripts minted is v4 and is fair game to re-derive.
                cursor.execute(
                    """
                    SELECT game_id, game_name, category
                      FROM gamebuddy.games
                     WHERE substring(game_id from 15 for 1) = '4'
                     ORDER BY game_name
                    """
                )
            rows = cursor.fetchall()

        print(f"{len(rows)} game(s) to re-derive.\n")

        changes: list[tuple[str, str, str, str]] = []
        unmatched: list[str] = []

        for game_id, name, current in rows:
            match = resolve(name, client_id, token)
            if match is None:
                unmatched.append(name)
                continue
            record = details(match.igdb_id, headers)
            if record is None:
                unmatched.append(name)
                continue
            # categorise() keys its overrides off the catalogue's name, not IGDB's.
            record["name"] = name
            derived = categorise(record)
            if derived != current:
                changes.append((game_id, name, current, derived))
                print(f"  {name[:40]:42} {current:14} -> {derived}")

        print(f"\n{len(changes)} would change, {len(rows) - len(changes)} already correct, "
              f"{len(unmatched)} unmatched.")
        for name in unmatched:
            print(f"  !  {name}: not identified on IGDB, left alone")

        if changes:
            moves = collections.Counter(f"{c} -> {d}" for _, _, c, d in changes)
            print("\nmost common moves:")
            for move, count in moves.most_common(12):
                print(f"  {count:4}  {move}")

        if not args.apply:
            print("\nDry run — nothing written. Re-run with --apply.")
            return 0

        with connection.cursor() as cursor:
            for game_id, _, _, derived in changes:
                cursor.execute(
                    "UPDATE gamebuddy.games SET category = %s WHERE game_id = %s",
                    (derived, game_id),
                )
        connection.commit()
        print(f"\nUpdated {len(changes)} game(s).")

        with connection.cursor() as cursor:
            cursor.execute(
                "SELECT category, count(*) FROM gamebuddy.games "
                "GROUP BY category ORDER BY count(*) DESC"
            )
            print("\nCatalogue genres now:")
            for category, count in cursor.fetchall():
                print(f"  {category:14} {count:4}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
