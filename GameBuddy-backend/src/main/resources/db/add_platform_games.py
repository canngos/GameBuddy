#!/usr/bin/env python3
"""Widen the catalogue so every platform's own games are in it, not just Steam's.

``add_popular_games.py`` reads IGDB's popularity rankings, and its own comment admits what
that costs: the strongest signals it trusts are ``24hr Peak Players`` and ``Global Top
Sellers``, both of which are Steam. A Steam-shaped list is a fine list of PC games and a
poor catalogue for anyone else — the picker ended up without Donkey Kong Bananza while a
Switch owner was being asked to choose the games they play.

So this does not rank globally and hope consoles appear. It asks IGDB the same question
once per platform, and takes the top N of each.

**Two passes per platform, because one is always wrong.** Sorting by ``total_rating_count``
returns the canon — Breath of the Wild, God of War — and almost nothing from the last two
years, since ratings accumulate slowly. Sorting by ``follows`` returns what people are
interested in now and under-weights the classics everybody already owns. A catalogue for
matching people needs both: the game someone has played for six years and the one they
bought last month are equally good reasons to swipe right. So each platform contributes
its most-rated *and* its most-followed-recent, interleaved.

**Multi-platform games are added once.** GTA V is on all five and must be one row: two
entries for one game split the people who play it across both, which is the single thing a
matching catalogue must never do. The first platform to claim a game keeps it, and the
per-platform counts in the dry run report what each one actually contributed.

**The erotic filter is inherited deliberately.** It is applied in the query and again in
Python for the reason ``add_popular_games`` sets out at length: an Apicalypse array
operator that is subtly misread would put pornography in the screen every new account
scrolls through. This script must never be the one that skips it.

Run the dry run first. It prints exactly what it would add, per platform, and writes
nothing:

    python add_platform_games.py                  # dry run
    python add_platform_games.py --apply
    python add_platform_games.py --count 30       # per platform. Default 50.

Same environment as its sibling: IGDB_CLIENT_ID, IGDB_CLIENT_SECRET, DATABASE_URL and the
R2_* variables. Run it from this directory — it imports the categoriser, the name
normaliser and the cover upload from ``add_popular_games`` rather than keeping a second
copy of rules that must not drift.
"""

from __future__ import annotations

import argparse
import os
import sys
import time
import uuid

import boto3
import psycopg
import requests

from add_popular_games import (
    EROTIC_THEME,
    EXCLUDE_NAMES,
    GAME_TYPES,
    IGDB_GAMES_URL,
    REQUEST_INTERVAL_SECONDS,
    categorise,
    igdb_token,
    normalise,
    upload_cover,
)

IGDB_PLATFORMS_URL = "https://api.igdb.com/v4/platforms"

# The app's five platforms, and the IGDB platforms each one means.
#
# **The keys are Platform enum names**, spelled as common/enums/Platform.java spells them,
# because they are written verbatim into gamebuddy.game_platform.platform. Using the enum's
# label ("Nintendo Switch") would read better here and store a value the backend cannot
# deserialise. If a platform is ever added to that enum, it is added here too or its games
# are silently tagged with nothing.
#
# The IGDB platforms are looked up by NAME at runtime rather than hard-coded as ids. IGDB's
# numeric ids are stable in practice, but a wrong one fails silently — the query simply
# returns another platform's games, and nobody notices until a Switch owner is offered Xbox
# exclusives. A name that stops resolving is loud instead, and the script refuses to run.
#
# Each is a generation pair on purpose. A PlayStation owner means PS4 or PS5 and does not
# think of them as different catalogues; restricting to PS5 alone would drop most of what
# people actually still play. The app collapses the generations deliberately — nobody
# picking games thinks of PS4 and PS5 as different libraries.
PLATFORM_FAMILIES: dict[str, tuple[str, ...]] = {
    "PC": ("PC (Microsoft Windows)",),
    "PLAYSTATION": ("PlayStation 4", "PlayStation 5"),
    "XBOX": ("Xbox One", "Xbox Series X|S"),
    "SWITCH": ("Nintendo Switch", "Nintendo Switch 2"),
    "MOBILE": ("Android", "iOS"),
}

# How far back "recent" reaches for the second pass. Three years is two console holiday
# seasons plus the current one — long enough that a 2023 hit still counts, short enough
# that the pass does not simply re-run the ratings pass.
RECENT_YEARS = 3

# The floor the recent pass has to clear, in IGDB follows.
#
# Without it the pass is worse than useless on consoles. "Most followed, released recently"
# sounds like a quality signal and is not one on its own: a new release competes only
# against other new releases, so on the Switch eShop the top of that list came back as
# "Primary School: English Grade 1 & 2", "Bubble Shooter: Color Splash" and a dozen more
# asset flips, which would have gone into the screen every new account picks its games from.
# Nearly two thirds of every platform's additions were 2025-26 titles nobody follows.
#
# Follows rather than ratings because ratings accumulate too slowly to judge a game released
# this year — which is the entire population this pass looks at. A genuine new hit clears
# this comfortably; shovelware has single digits.
MIN_FOLLOWS_RECENT = 20

FIELDS = (
    "fields name,summary,cover.image_id,genres.id,genres.name,themes,"
    "total_rating_count,follows,first_release_date,platforms;"
)


def resolve_platform_ids(headers: dict) -> dict[str, list[int]]:
    """IGDB platform ids for each of the app's platforms, by name."""
    wanted = {name for names in PLATFORM_FAMILIES.values() for name in names}

    time.sleep(REQUEST_INTERVAL_SECONDS)
    response = requests.post(
        IGDB_PLATFORMS_URL,
        headers=headers,
        data="fields id,name; limit 500;",
        timeout=25,
    )
    response.raise_for_status()
    by_name = {row["name"]: row["id"] for row in response.json()}

    missing = sorted(wanted - by_name.keys())
    if missing:
        # Refuse rather than quietly build a narrower catalogue than asked for.
        raise SystemExit(
            "IGDB has no platform named: "
            + ", ".join(missing)
            + "\nIt has probably been renamed. Fix PLATFORM_FAMILIES before running."
        )

    resolved = {
        app_platform: [by_name[name] for name in names]
        for app_platform, names in PLATFORM_FAMILIES.items()
    }
    for app_platform, ids in resolved.items():
        print(f"  {app_platform:12} -> IGDB {ids}")
    return resolved


def platform_lookup(resolved: dict[str, list[int]]) -> dict[int, str]:
    """IGDB platform id to the Platform enum name it counts as, for tagging.

    The inverse of PLATFORM_FAMILIES, and lossy on purpose: an id the app has no platform
    for — PS3, Mac, Linux, Stadia — is simply absent, so tagging skips it rather than
    inventing a sixth platform the enum cannot store.
    """
    return {igdb_id: name for name, ids in resolved.items() for igdb_id in ids}


def tags_for(game: dict, tag_for: dict[int, str]) -> list[str]:
    """The Platform enum names to record for one game.

    Sorted and de-duplicated because PS4 and PS5 both collapse to PLAYSTATION, and writing
    that twice would violate the (game_id, platform) primary key and abort the insert.

    Falls back to OTHER rather than to nothing. A game IGDB lists only on the SNES is not a
    game we failed to look up, and leaving it empty spells those two very differently-fixable
    situations the same way.
    """
    tags = sorted({tag_for[p] for p in (game.get("platforms") or []) if p in tag_for})
    return tags or ["OTHER"]


def query(headers: dict, where: str, sort: str, limit: int) -> list[dict]:
    """One Apicalypse query, with the unshowable and the pornographic already excluded."""
    time.sleep(REQUEST_INTERVAL_SECONDS)
    response = requests.post(
        IGDB_GAMES_URL,
        headers=headers,
        data=(
            f"{FIELDS} "
            f"where {where} "
            f"& version_parent = null & game_type = {GAME_TYPES} "
            # Same four reasons as the sibling script: nothing to draw, nothing to
            # categorise by, not actually released, or pornography.
            f"& themes != ({EROTIC_THEME}) & cover != null & genres != null "
            "& first_release_date != null; "
            f"sort {sort}; limit {limit};"
        ),
        timeout=25,
    )
    response.raise_for_status()

    # The Python half of the erotic filter. See add_popular_games.fetch_games for why this
    # is written twice and why the duplication is not redundant.
    return [g for g in response.json() if EROTIC_THEME not in (g.get("themes") or [])]


def candidates_for(headers: dict, platform_ids: list[int], count: int) -> list[dict]:
    """The games worth offering to owners of one platform, best first.

    Interleaved rather than concatenated: taking all the most-rated and then all the
    most-followed would spend the whole quota on the canon and reach the recent pass only
    for platforms with few games. One from each in turn keeps both halves represented
    whatever the count is set to.
    """
    # `= (a,b)` is Apicalypse for "contains any of", which is what a platform family means.
    on_platform = f"platforms = ({','.join(str(i) for i in platform_ids)})"
    cutoff = int(time.time()) - RECENT_YEARS * 365 * 24 * 3600

    established = query(headers, on_platform, "total_rating_count desc", count * 2)
    recent = query(
        headers,
        f"{on_platform} & first_release_date > {cutoff} & follows >= {MIN_FOLLOWS_RECENT}",
        "follows desc",
        count * 2,
    )

    merged: list[dict] = []
    seen: set[int] = set()
    for pair in zip(established, recent):
        for game in pair:
            if game["id"] not in seen:
                seen.add(game["id"])
                merged.append(game)
    # Whichever list was longer still has a tail worth keeping.
    for game in established + recent:
        if game["id"] not in seen:
            seen.add(game["id"])
            merged.append(game)
    return merged


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true", help="actually write")
    parser.add_argument(
        "--count", type=int, default=50, help="how many per platform (default 50)"
    )
    args = parser.parse_args()

    missing = [
        name
        for name in (
            "IGDB_CLIENT_ID", "IGDB_CLIENT_SECRET", "DATABASE_URL", "R2_ENDPOINT",
            "R2_ACCESS_KEY_ID", "R2_SECRET_ACCESS_KEY", "R2_MEDIA_BUCKET", "R2_PUBLIC_URL",
        )
        if not os.environ.get(name)
    ]
    if missing:
        print(f"Missing environment variables: {', '.join(missing)}", file=sys.stderr)
        return 1

    client_id = os.environ["IGDB_CLIENT_ID"]
    token = igdb_token(client_id, os.environ["IGDB_CLIENT_SECRET"])
    headers = {"Client-ID": client_id, "Authorization": f"Bearer {token}"}

    s3 = boto3.client(
        "s3",
        endpoint_url=os.environ["R2_ENDPOINT"],
        aws_access_key_id=os.environ["R2_ACCESS_KEY_ID"],
        aws_secret_access_key=os.environ["R2_SECRET_ACCESS_KEY"],
        region_name="auto",
    )
    bucket = os.environ["R2_MEDIA_BUCKET"]
    public_url = os.environ["R2_PUBLIC_URL"]

    print("Resolving IGDB platform ids...")
    platform_ids = resolve_platform_ids(headers)
    tag_for = platform_lookup(platform_ids)

    with psycopg.connect(os.environ["DATABASE_URL"]) as connection:
        with connection.cursor() as cursor:
            cursor.execute("SELECT game_name FROM gamebuddy.games")
            existing = {normalise(row[0]) for row in cursor.fetchall()}
        print(f"\n{len(existing)} games already in the catalogue.\n")

        # Ordered so the platforms most likely to be under-served claim their exclusives
        # before PC — which is the whole point — takes another hundred multi-platform rows.
        order = ["SWITCH", "PLAYSTATION", "XBOX", "MOBILE", "PC"]

        additions: list[dict] = []
        per_platform: dict[str, list[str]] = {}

        for app_platform in order:
            found = candidates_for(headers, platform_ids[app_platform], args.count)
            taken: list[str] = []
            for game in found:
                if len(taken) >= args.count:
                    break
                key = normalise(game["name"])
                if key in existing or key in EXCLUDE_NAMES:
                    continue
                existing.add(key)
                taken.append(game["name"])
                additions.append(game)
            per_platform[app_platform] = taken
            print(f"{app_platform:16} {len(found):4} candidates -> {len(taken):3} new")

        print(f"\n{len(additions)} new games in total.\n")
        for app_platform in order:
            names = per_platform[app_platform]
            if not names:
                continue
            print(f"--- {app_platform} ({len(names)}) ---")
            for game in additions:
                if game["name"] in names:
                    year = time.strftime("%Y", time.gmtime(game["first_release_date"]))
                    tags = ",".join(tags_for(game, tag_for)) or "-"
                    print(
                        f"  + {game['name'][:40]:42} {year}  "
                        f"{categorise(game):11} {tags}"
                    )
            print()

        if not args.apply:
            print("Dry run — nothing written. Re-run with --apply.")
            return 0

        written = 0
        for game in additions:
            game_id = str(uuid.uuid4())
            try:
                stored = upload_cover(
                    s3, bucket, public_url, game_id, game["cover"]["image_id"]
                )
            except (requests.RequestException, OSError) as error:
                print(f"  !  {game['name']}: cover upload failed ({error})")
                continue

            with connection.cursor() as cursor:
                cursor.execute(
                    """
                    INSERT INTO gamebuddy.games
                        (game_id, game_name, category, description, game_icon,
                         avg_vote, is_popular)
                    VALUES (%s, %s, %s, %s, %s, %s, true)
                    """,
                    (
                        game_id,
                        game["name"][:255],
                        categorise(game),
                        (game.get("summary") or "")[:255] or None,
                        stored,
                        None,
                    ),
                )
                # Tagged in the same transaction as the row it describes. A game that
                # exists with no platforms is not a smaller problem than one that does not
                # exist — it is the one that sorts into "everything else" forever, because
                # nothing later goes looking for rows that were missed.
                tags = tags_for(game, tag_for)
                if tags:
                    cursor.executemany(
                        """
                        INSERT INTO gamebuddy.game_platform (game_id, platform)
                        VALUES (%s, %s)
                        ON CONFLICT (game_id, platform) DO NOTHING
                        """,
                        [(game_id, tag) for tag in tags],
                    )
                else:
                    # Worth saying out loud. IGDB knows the game but lists it only on
                    # platforms the app has no enum for — PS3, Linux, Stadia — so it will
                    # sit below the grouped section for everyone.
                    print(f"  ~  {game['name']}: no platform the app recognises")
            written += 1

        connection.commit()
        print(f"\nAdded {written} game(s).")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
