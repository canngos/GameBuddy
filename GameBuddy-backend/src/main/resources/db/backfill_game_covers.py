#!/usr/bin/env python3
"""Fill in game cover art and descriptions from IGDB.

Every one of the hundred catalogue games has an empty ``game_icon`` and a description
reading "<name> - seeded for local development". The picker now shows both, so both need
to be real.

**Why IGDB and not a web search.** Cover art is copyrighted by its publisher. Downloading
box art off the open web and shipping it inside a commercial app is infringement, and
publishers do send takedowns. IGDB is Amazon/Twitch-owned, free, and licensed for exactly
this use. Their terms ask for attribution, which the app carries in Settings -> About.

**Why the images are copied to R2 rather than hotlinked.** Hotlinking puts somebody else's
CDN on the path of your onboarding: their outage is your blank screen, their URL change is
your broken catalogue, and their rate limit is your user's problem. Copying once costs a
few megabytes and removes all three.

Run it once, from anywhere with database and R2 access:

    export IGDB_CLIENT_ID=...        # https://dev.twitch.tv/console/apps
    export IGDB_CLIENT_SECRET=...
    export DATABASE_URL=postgresql://gamebuddy:...@localhost:5432/gamebuddy
    export R2_ENDPOINT=... R2_ACCESS_KEY_ID=... R2_SECRET_ACCESS_KEY=...
    export R2_MEDIA_BUCKET=gamebuddy-media R2_PUBLIC_URL=https://...

    pip install psycopg[binary] boto3 requests
    python backfill_game_covers.py            # dry run: reports, changes nothing
    python backfill_game_covers.py --apply

Idempotent: rows that already have a cover are skipped, so a partial run can simply be
run again.
"""

from __future__ import annotations

import argparse
import difflib
import os
import re
import sys
import time
from dataclasses import dataclass

import boto3
import psycopg
import requests

IGDB_TOKEN_URL = "https://id.twitch.tv/oauth2/token"
IGDB_GAMES_URL = "https://api.igdb.com/v4/games"

# IGDB asks for four requests a second. Staying under it by a clear margin costs a couple
# of minutes across a hundred games and avoids being throttled halfway through.
REQUEST_INTERVAL_SECONDS = 0.30

# t_cover_big is 264x374 — about twice the size this renders at, so it still looks right
# on a 3x screen without shipping a 1080p image to a phone for a thumbnail.
COVER_SIZE = "t_cover_big"

# Which IGDB entry kinds count as "the game somebody means".
#
# This was `game_type = 0` — main game only — and that quietly lost the real entry for
# some of the best-known titles in the catalogue: IGDB files Minecraft as a port (11),
# Persona 5 Royal as an expanded game (10), Mass Effect Legendary Edition as a bundle (3)
# and ARK: Survival Ascended as a remaster (9). Excluded are the kinds that are not a game
# you play on their own: DLC (1), expansion (2), bundled DLC (5), season (7), pack (13).
GAME_TYPES = "(0,3,8,9,10,11)"

# Catalogue names IGDB spells differently, or does not return from a fuzzy search at all.
#
# An int is an IGDB id and is fetched directly — no search, no similarity check, no chance
# of drifting to a different game. Used where the name is genuinely ambiguous: there are
# five entries called "Resident Evil 4" across four release years, and IGDB's search puts
# 2016's Doom above 1993's for the query "Doom".
#
# A string is a search term, used where IGDB simply files the game under a fuller name.
OVERRIDES: dict[str, int | str] = {
    "The Finals": 214417,
    "The Forest": 7504,
    "Doom (1993)": 673,
    "Resident Evil 4 Remake": 132181,
    "Civilization VII": "Sid Meier's Civilization VII",
    "Final Fantasy XIV": "Final Fantasy XIV Online",
    "REPO": "R.E.P.O.",
    "Black Desert Online": "Black Desert",
    "Football Manager 2025": "Football Manager 25",
    # IGDB has no "Overwatch 2" entry — only seasons and cosmetic bundles. Its "Overwatch"
    # record is the 2023 one, which is the game people are actually playing, so the
    # catalogue follows it and takes that name and cover.
    "Overwatch 2": "Overwatch",
    # Same shape, different conclusion than this file first reached. IGDB files the 2026
    # release only as sponsored editions ("eFootball: Leo Messi Edition 2026"), none of
    # which is the game, so eFootball 2026 was left unmatched and rendered as the lettered
    # fallback. Called by its plain name, IGDB's evergreen "eFootball" record is the same
    # continuously-updated free-to-play title the catalogue means — so it supplies the
    # cover and the description, and KEEP_OUR_NAME below holds the year in the catalogue.
    "eFootball 2026": "eFootball",
}

# Catalogue names that keep their own spelling instead of adopting IGDB's.
#
# IGDB's titles are more accurate than the hand-typed catalogue and are used everywhere
# else, but a few are catalogue entries rather than titles: IGDB calls Dragon Quest XI S
# "Dragon Quest XI S: Echoes of an Elusive Age - Definitive Edition", which is correct and
# also seventy characters that will wrap to three lines in a card two-across on a phone.
#
# This does not change which entry is matched — only the name that gets stored — so the
# cover and description still come from the full record.
KEEP_OUR_NAME = {
    "Dragon Quest XI S",
    # Borrows IGDB's evergreen "eFootball" record for art and text, but the catalogue sells
    # the current season and players look for the year. Without this the entry would rename
    # itself to "eFootball" and stop matching what is on the box.
    "eFootball 2026",
}


@dataclass
class Match:
    igdb_id: int
    name: str
    cover_url: str | None
    summary: str | None


def igdb_token(client_id: str, client_secret: str) -> str:
    response = requests.post(
        IGDB_TOKEN_URL,
        params={
            "client_id": client_id,
            "client_secret": client_secret,
            "grant_type": "client_credentials",
        },
        timeout=20,
    )
    response.raise_for_status()
    return response.json()["access_token"]


# Roman numerals as far as any sequel gets. "Slay the Spire 2" and "Slay the Spire II"
# are the same game and must compare equal, or every numbered sequel is a false miss.
ROMAN_NUMERALS = {
    "ii": "2", "iii": "3", "iv": "4", "v": "5", "vi": "6", "vii": "7",
    "viii": "8", "ix": "9", "x": "10", "xi": "11", "xii": "12",
}

# How close a title has to be before the cover is trusted. Measured against the real
# catalogue: correct matches score 1.00, and the closest wrong one — "Overwatch 2"
# resolving to "Overwatch" — scores 0.90. Anything under this is reported rather than
# guessed at.
CONFIDENCE = 0.95


def normalise(title: str) -> str:
    """Lower case, punctuation stripped, roman numerals as digits."""
    text = re.sub(r"[^a-z0-9 ]", " ", title.lower())
    text = re.sub(r"\s+", " ", text).strip()
    return " ".join(ROMAN_NUMERALS.get(word, word) for word in text.split())


def is_subtitled(wanted: str, candidate: str) -> bool:
    """True when the candidate is the wanted game plus a subtitle.

    "StarCraft II" and "StarCraft II: Wings of Liberty" are the same game, and pure string
    similarity scores that at 0.72 — below the threshold — so it was being thrown away.

    The separator is what makes this safe. Requiring a colon or a dash after the matched
    part means "Doom" does not swallow "Doom Eternal", which is a different game with no
    punctuation between the words. Only an actual subtitle counts.
    """
    prefix = normalise(wanted)
    if len(prefix) < 6:
        # Too short to be distinctive: a three-letter name prefixes half the catalogue.
        return False

    for separator in (":", " -", " –"):
        head, found, _ = candidate.partition(separator)
        if found and normalise(head) == prefix:
            return True
    return False


def fetch_by_id(igdb_id: int, client_id: str, token: str) -> Match | None:
    """One specific IGDB entry, pinned by id.

    No search and no similarity check: an id is an assertion that this exact entry is the
    right one, made by a person who looked. That is the only honest way to disambiguate
    five games called "Resident Evil 4".
    """
    query = f"fields name,cover.image_id,summary; where id = {igdb_id}; limit 1;"
    response = requests.post(
        IGDB_GAMES_URL,
        headers={"Client-ID": client_id, "Authorization": f"Bearer {token}"},
        data=query,
        timeout=20,
    )
    response.raise_for_status()
    results = response.json()
    if not results:
        return None

    game = results[0]
    image_id = (game.get("cover") or {}).get("image_id")
    if not image_id:
        return None
    return Match(
        igdb_id=game["id"],
        name=game.get("name", ""),
        cover_url=f"https://images.igdb.com/igdb/image/upload/{COVER_SIZE}/{image_id}.jpg",
        summary=game.get("summary"),
    )


def resolve(name: str, client_id: str, token: str) -> Match | None:
    """The IGDB entry for a catalogue game, applying any override first."""
    override = OVERRIDES.get(name)
    if isinstance(override, int):
        return fetch_by_id(override, client_id, token)
    return search(override or name, client_id, token)


def search(name: str, client_id: str, token: str) -> Match | None:
    """The game IGDB has under this name, or None when it cannot be identified confidently.

    **Name similarity decides, not popularity.** Ranking candidates by rating count alone
    is what made "The Finals" resolve to Final Fantasy and "The Forest" to Sons of the
    Forest: IGDB's search is fuzzy, and the most-rated result for a vague query is
    whatever famous game shares a word with it. Rating count is still used, but only to
    break ties between titles that are already equally close.

    A game that cannot be matched confidently gets no cover and is reported. The picker
    falls back to the game's initial, which is honest; the wrong box art is not, and
    somebody choosing what they play should never be shown a different game and told it is
    the same one.

    `game_type = 0` is main games only — not DLC, bundles or remaster entries. That field
    used to be called `category`, and IGDB renamed it. The old name does not error: the
    filter matches nothing and the endpoint returns `[]` with HTTP 200, so every lookup
    came back empty including Elden Ring. A filter that silently matches nothing is worse
    than one that fails.

    `version_parent = null` drops regional and re-released duplicates.
    """
    query = (
        f'search "{name}"; '
        "fields name,cover.image_id,summary,total_rating_count; "
        f"where version_parent = null & game_type = {GAME_TYPES}; "
        # Fifteen rather than five: the exact title is often not the most popular result,
        # and it has to be in the page at all before it can be preferred.
        "limit 15;"
    )
    response = requests.post(
        IGDB_GAMES_URL,
        headers={"Client-ID": client_id, "Authorization": f"Bearer {token}"},
        data=query,
        timeout=20,
    )
    response.raise_for_status()

    target = normalise(name)
    candidates = []
    for game in response.json():
        image_id = (game.get("cover") or {}).get("image_id")
        if not image_id:
            continue
        similarity = difflib.SequenceMatcher(None, target, normalise(game["name"])).ratio()
        candidates.append((similarity, game.get("total_rating_count") or 0, game, image_id))

    # Acceptability first, popularity second — and in that order, which is the whole
    # point. Taking the most *similar* candidate and then checking whether it is good
    # enough loses the right answer whenever a bad candidate happens to score higher:
    # "Age of Empires II Mobile" scores 0.82 against "Age of Empires II" while
    # "Age of Empires II: The Age of Kings" scores only 0.65, so the mobile port won the
    # comparison, failed the threshold, and took the real game down with it.
    #
    # Among candidates that are already established as the same game, the most-rated one
    # is the canonical entry — the original release rather than a spin-off or a bundle.
    acceptable = [
        c for c in candidates if c[0] >= CONFIDENCE or is_subtitled(name, c[2]["name"])
    ]
    if not acceptable:
        return None

    _, _, game, image_id = max(acceptable, key=lambda c: c[1])

    return Match(
        igdb_id=game["id"],
        name=game.get("name", name),
        cover_url=f"https://images.igdb.com/igdb/image/upload/{COVER_SIZE}/{image_id}.jpg",
        summary=game.get("summary"),
    )


def upload_cover(s3, bucket: str, public_url: str, game_id: str, cover_url: str) -> str:
    image = requests.get(cover_url, timeout=30)
    image.raise_for_status()

    key = f"covers/{game_id}.jpg"
    s3.put_object(
        Bucket=bucket,
        Key=key,
        Body=image.content,
        ContentType="image/jpeg",
        # Immutable: the key contains the game id and the cover for a given game does not
        # change. A year lets the CDN and the phone both stop asking.
        CacheControl="public, max-age=31536000, immutable",
    )
    return f"{public_url.rstrip('/')}/{key}"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--apply",
        action="store_true",
        help="actually write. Without it the script reports what it would do and exits.",
    )
    parser.add_argument(
        "--limit", type=int, default=0, help="stop after N games. 0 means all of them."
    )
    args = parser.parse_args()

    required = [
        "IGDB_CLIENT_ID",
        "IGDB_CLIENT_SECRET",
        "DATABASE_URL",
        "R2_ENDPOINT",
        "R2_ACCESS_KEY_ID",
        "R2_SECRET_ACCESS_KEY",
        "R2_MEDIA_BUCKET",
        "R2_PUBLIC_URL",
    ]
    missing = [name for name in required if not os.environ.get(name)]
    if missing:
        print(f"Missing environment variables: {', '.join(missing)}", file=sys.stderr)
        return 1

    client_id = os.environ["IGDB_CLIENT_ID"]
    token = igdb_token(client_id, os.environ["IGDB_CLIENT_SECRET"])
    print("IGDB token acquired.")

    s3 = boto3.client(
        "s3",
        endpoint_url=os.environ["R2_ENDPOINT"],
        aws_access_key_id=os.environ["R2_ACCESS_KEY_ID"],
        aws_secret_access_key=os.environ["R2_SECRET_ACCESS_KEY"],
        region_name="auto",
    )
    bucket = os.environ["R2_MEDIA_BUCKET"]
    public_url = os.environ["R2_PUBLIC_URL"]

    found = missed = uploaded = 0

    with psycopg.connect(os.environ["DATABASE_URL"]) as connection:
        with connection.cursor() as cursor:
            cursor.execute(
                """
                SELECT game_id, game_name
                FROM gamebuddy.games
                WHERE game_icon IS NULL OR game_icon = ''
                ORDER BY game_name
                """
            )
            games = cursor.fetchall()

        if args.limit:
            games = games[: args.limit]
        print(f"{len(games)} game(s) without a cover.\n")

        for game_id, game_name in games:
            time.sleep(REQUEST_INTERVAL_SECONDS)
            try:
                match = resolve(game_name, client_id, token)
            except requests.RequestException as error:
                print(f"  !  {game_name}: IGDB request failed ({error})")
                missed += 1
                continue

            if not match or not match.cover_url:
                # Reported, not guessed at. A wrong cover is worse than none: somebody
                # picking "Battlefield" should not be shown the box art for a different
                # game and quietly told it is the same thing.
                print(f"  ?  {game_name}: no cover on IGDB")
                missed += 1
                continue

            found += 1
            kept = game_name in KEEP_OUR_NAME
            note = (
                ""
                if kept or match.name.lower() == game_name.lower()
                else f"  -> renamed: {match.name}"
            )
            print(f"  ok {game_name}{note}")

            if not args.apply:
                continue

            stored_name = game_name if game_name in KEEP_OUR_NAME else match.name

            try:
                stored = upload_cover(s3, bucket, public_url, game_id, match.cover_url)
            except (requests.RequestException, OSError) as error:
                print(f"  !  {game_name}: cover upload failed ({error})")
                missed += 1
                continue

            # IGDB's title replaces the seeded one. The catalogue was typed by hand and
            # carries its own spellings — "REPO" for R.E.P.O., "Civilization VII" without
            # the "Sid Meier's" — while IGDB is a maintained database of what these games
            # are actually called.
            #
            # The id never changes, so the join tables and everyone's profile are fine.
            # **The trained model is not**, and this comment used to claim otherwise.
            # The recommender is keyed on game *names*, not ids: `features.py` builds its
            # vocabulary from the strings, and the backend's cold-start path sends
            # `Games::getGameName` — this value — to `/predict/cold-start`. Rename a game
            # here without retraining and the model silently stops recognising it, because
            # `transform` drops unknown vocabulary rather than raising. Running this once
            # renamed 17 of 100 titles and cost every affected game its entire contribution
            # to cold-start ranking, with nothing anywhere reporting a problem.
            #
            # So: after applying this, update `gamebuddy_model/catalogue.py` to match and
            # retrain. `tests/test_pipeline.py::test_catalogue_names_match_the_database_seed`
            # fails if the two drift apart again.
            with connection.cursor() as cursor:
                cursor.execute(
                    """
                    UPDATE gamebuddy.games
                    SET game_icon = %s,
                        game_name = %s,
                        description = COALESCE(NULLIF(%s, ''), description)
                    WHERE game_id = %s
                    """,
                    (stored, stored_name, (match.summary or "")[:255], game_id),
                )
            uploaded += 1

        if args.apply:
            connection.commit()

    print(f"\nmatched {found}, no cover {missed}, written {uploaded}")
    if not args.apply:
        print("Dry run — nothing was written. Re-run with --apply.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
