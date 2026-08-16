#!/usr/bin/env python3
"""Add the popular games IGDB knows about and the catalogue does not.

The seeded catalogue was typed by hand and shows it — no NBA 2K26, no WWE 2K26, nothing
released since whenever the list was written. This pulls IGDB's own popularity rankings,
drops everything already present, and adds the rest with covers and descriptions.

**Popularity, not ratings.** A rating count is a poor proxy for "a game people are playing
now": NBA 2K26 has seventeen ratings and WWE 2K26 has three, because they are new. IGDB
publishes actual popularity signals — site visits, players, Steam peak concurrents, top
sellers — and those are what this reads.

**Why the erotic filter is not optional.** IGDB's popularity rankings are, unfiltered, full
of pornography: "Porno Studio Tycoon", "Oppai Slider 2", "Femboy Futa House" and worse all
rank inside the top forty by visits. Importing that list blind would put those titles in
the screen every new account picks their games from. A rating-count threshold does not
help — "Artificial Academy 2" has 37 ratings, twice NBA 2K26's. IGDB's Erotic theme (42)
does, and it flagged every one of them when checked.

Run the dry run first. It prints exactly what it would add and writes nothing:

    python run_add_games.py                 # dry run
    python run_add_games.py --apply
    python run_add_games.py --count 40      # how many to add. Default 60.
"""

from __future__ import annotations

import argparse
import os
import re
import sys
import time
import uuid

import boto3
import psycopg
import requests

IGDB_TOKEN_URL = "https://id.twitch.tv/oauth2/token"
IGDB_GAMES_URL = "https://api.igdb.com/v4/games"
IGDB_POPULARITY_URL = "https://api.igdb.com/v4/popularity_primitives"
IGDB_KEYWORDS_URL = "https://api.igdb.com/v4/keywords"

REQUEST_INTERVAL_SECONDS = 0.30
COVER_SIZE = "t_cover_big"

# Which popularity signals to read, in the order they are trusted.
#   1  Visits              — IGDB page views. Broad, and the noisiest.
#   3  Playing             — marked as currently playing.
#   5  24hr Peak Players   — Steam concurrents. Strong signal, PC-biased.
#   9  Global Top Sellers  — Steam sales. Catches new releases before anyone rates them.
POPULARITY_TYPES = (5, 9, 3, 1)

# IGDB's Erotic theme. See the module docstring: this is the filter that keeps pornography
# out of the onboarding screen, and it is not a matter of taste — an 18+ rating covers
# dating, not a catalogue of porn games in a list every account holder scrolls through.
EROTIC_THEME = 42

GAME_TYPES = "(0,3,8,9,10,11)"

# The five categories IGDB files under neither genre nor theme.
#
# The hand-typed seed used JRPG, MMO, Roguelike, Metroidvania and Battle Royale, and the
# rules below could not produce any of them — so a JRPG added by this script was filed as
# RPG and the catalogue ended up speaking two vocabularies at once. Invisible while genre
# was only a label on a card; a bug the moment genre became a filter, because tapping
# "JRPG" returned the eight seeded ones and none of the newer ones.
#
# IGDB does know all five, just not where the rules were looking:
#
#   game_modes  carries MMO (5) and Battle Royale (6) as first-class values.
#   keywords    carry roguelike, metroidvania and jrpg — community tags rather than
#               taxonomy, which is exactly why they capture how people describe games.
#
# Keyword ids are resolved by name at runtime, for the same reason platform ids are: a
# hard-coded id that drifts fails silently, and silently means a whole category quietly
# stops being produced.
KEYWORD_CATEGORIES: dict[str, str] = {
    "metroidvania": "Metroidvania",
    "roguelike": "Roguelike",
    "roguelite": "Roguelike",
    "rogue-like": "Roguelike",
    "jrpg": "JRPG",
}

# IGDB game_mode id -> category. 5 is Massively Multiplayer Online, 6 is Battle Royale.
MODE_CATEGORIES: dict[int, str] = {
    5: "MMO",
    6: "Battle Royale",
}

# Filled by load_keyword_categories(). Module-level rather than a parameter so that every
# existing categorise() call site benefits without changing its signature.
_KEYWORD_IDS: dict[int, str] = {}


def load_keyword_categories(headers: dict) -> dict[int, str]:
    """Resolve the keyword names above to IGDB ids, once per run."""
    global _KEYWORD_IDS
    wanted = sorted(KEYWORD_CATEGORIES)
    names = ",".join(f'"{name}"' for name in wanted)
    time.sleep(REQUEST_INTERVAL_SECONDS)
    response = requests.post(
        IGDB_KEYWORDS_URL,
        headers=headers,
        data=f"fields id,name; where name = ({names}); limit 50;",
        timeout=25,
    )
    response.raise_for_status()
    _KEYWORD_IDS = {
        row["id"]: KEYWORD_CATEGORIES[row["name"]]
        for row in response.json()
        if row["name"] in KEYWORD_CATEGORIES
    }
    found = sorted({c for c in _KEYWORD_IDS.values()})
    print(f"  keyword categories resolved: {', '.join(found) or 'none'}")
    return _KEYWORD_IDS


# IGDB genre and theme ids to the categories this catalogue already uses.
#
# Ordered: the first rule that matches wins, so the specific ones come before the broad.
# "Horror" and "Survival" are themes rather than genres in IGDB and are checked first
# because they describe a game better than "Shooter" does when both apply — Resident Evil
# is a horror game that happens to contain a gun.
#
# Reached only after the keyword and game_mode checks above, which are narrower still.
CATEGORY_RULES: list[tuple[str, str, int]] = [
    ("themes", "Horror", 19),
    ("themes", "Survival", 21),
    ("genres", "MOBA", 36),
    # Racing before Sport: a driving sim carries both, and Assetto Corsa filed under
    # "Sports" next to NBA 2K is not what anybody is looking for.
    ("genres", "Racing", 10),
    ("genres", "Sports", 14),
    ("genres", "Fighting", 4),
    ("genres", "FPS", 5),
    ("genres", "Strategy", 11),
    ("genres", "Strategy", 15),
    ("genres", "Strategy", 16),
    ("genres", "Strategy", 24),
    ("genres", "RPG", 12),
    ("genres", "Simulation", 13),
    ("genres", "Platformer", 8),
    ("genres", "Puzzle", 9),
    ("themes", "Sandbox", 33),
    ("themes", "Party", 40),
    ("genres", "Adventure", 31),
]
DEFAULT_CATEGORY = "Adventure"

# Where the genre rules give an answer that is technically defensible and plainly wrong.
#
# IGDB hands out several genres per game and there is no ordering of them that is right
# every time: Grand Theft Auto V is tagged Shooter, Racing and Adventure, and any rule
# that files it correctly files something else wrongly. These are the handful worth
# stating outright rather than tuning the rules around.
CATEGORY_OVERRIDES = {
    "Grand Theft Auto V": "Adventure",
    "VRChat": "Simulation",
    "Deltarune": "RPG",
    "Geometry Dash": "Platformer",
}

# Popular enough to matter, but not inside IGDB's top-200 popularity lists — usually
# because they are new enough that the rankings have not caught up. Fetched by name.
ALWAYS_INCLUDE = (
    "NBA 2K26",
    "WWE 2K26",
)

# Not added, even though they rank. Every one is an edition of a game the catalogue
# already carries, and two entries for the same game split the people who play it across
# both — which is the one thing a matching catalogue must not do.
EXCLUDE_NAMES = {
    "ea sports fc 25",                        # the catalogue has FC 26
    "football manager 2024",                  # the catalogue has Football Manager 25
    "efootball 2022",                         # 2026 was removed deliberately
    "age of empires 2 definitive edition",    # the catalogue has The Age of Kings
    "grand theft auto 5 enhanced",            # the catalogue is getting GTA V itself
}

ROMAN_NUMERALS = {
    "ii": "2", "iii": "3", "iv": "4", "v": "5", "vi": "6", "vii": "7",
    "viii": "8", "ix": "9", "x": "10", "xi": "11", "xii": "12",
}


def normalise(title: str) -> str:
    text = re.sub(r"[^a-z0-9 ]", " ", title.lower())
    text = re.sub(r"\s+", " ", text).strip()
    return " ".join(ROMAN_NUMERALS.get(word, word) for word in text.split())


def categorise(game: dict) -> str:
    override = CATEGORY_OVERRIDES.get(game["name"])
    if override:
        return override

    # Most specific first, and these are more specific than any genre IGDB assigns.
    #
    # A roguelike is tagged Action and RPG as well, so leaving this until after the genre
    # rules would file Hades as RPG and never produce the category at all. Same for
    # Metroidvania, which is Platform + Adventure, and for battle royales, which are
    # Shooter. The narrower answer is the one somebody scrolling a genre filter wants.
    for keyword in game.get("keywords") or []:
        if keyword in _KEYWORD_IDS:
            return _KEYWORD_IDS[keyword]
    for mode in game.get("game_modes") or []:
        if mode in MODE_CATEGORIES:
            return MODE_CATEGORIES[mode]

    genres = {g["id"] for g in (game.get("genres") or [])}
    themes = set(game.get("themes") or [])
    for field, category, ident in CATEGORY_RULES:
        if ident in (themes if field == "themes" else genres):
            return category
    return DEFAULT_CATEGORY


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


def popular_game_ids(headers: dict) -> list[int]:
    """Game ids from every popularity signal, best signals first, de-duplicated."""
    ordered: list[int] = []
    for popularity_type in POPULARITY_TYPES:
        time.sleep(REQUEST_INTERVAL_SECONDS)
        response = requests.post(
            IGDB_POPULARITY_URL,
            headers=headers,
            data=(
                f"fields game_id,value; where popularity_type = {popularity_type}; "
                "sort value desc; limit 200;"
            ),
            timeout=25,
        )
        response.raise_for_status()
        ordered += [row["game_id"] for row in response.json()]

    return list(dict.fromkeys(ordered))


def fetch_games(ids: list[int], headers: dict) -> list[dict]:
    """Full records for candidate ids, with everything unusable already filtered out."""
    games: list[dict] = []
    for start in range(0, len(ids), 100):
        chunk = ids[start : start + 100]
        time.sleep(REQUEST_INTERVAL_SECONDS)
        response = requests.post(
            IGDB_GAMES_URL,
            headers=headers,
            data=(
                "fields name,summary,cover.image_id,genres.id,genres.name,themes,"
                "game_modes,keywords,total_rating_count,first_release_date; "
                f"where id = ({','.join(str(i) for i in chunk)}) "
                f"& version_parent = null & game_type = {GAME_TYPES} "
                # Every one of these is a reason the entry cannot be shown in the picker:
                # nothing to draw, nothing to categorise it by, or not actually out yet.
                f"& themes != ({EROTIC_THEME}) & cover != null & genres != null "
                "& first_release_date != null; "
                "limit 100;"
            ),
            timeout=25,
        )
        response.raise_for_status()

        # The same exclusion again, in Python.
        #
        # Not paranoia about typing it twice: `themes != (42)` is an Apicalypse array
        # operator, and if it ever means "not exactly equal to [42]" rather than "does not
        # contain 42", a game tagged Erotic *and* Fantasy sails straight through a filter
        # that looks correct. This check cannot be misread. It is the difference between
        # pornography being kept out of the onboarding screen and probably being kept out.
        games += [game for game in response.json() if EROTIC_THEME not in (game.get("themes") or [])]
    return games


def fetch_by_name(name: str, headers: dict) -> dict | None:
    """One game by name, for titles too new to appear in the popularity rankings.

    NBA 2K26 has seventeen ratings and WWE 2K26 has three; neither is inside IGDB's
    top two hundred by visits or players. They are still games people are looking for a
    second player in, which is what this catalogue is for.
    """
    time.sleep(REQUEST_INTERVAL_SECONDS)
    response = requests.post(
        IGDB_GAMES_URL,
        headers=headers,
        data=(
            f'search "{name}"; '
            "fields name,summary,cover.image_id,genres.id,genres.name,themes,"
            "total_rating_count,first_release_date; "
            f"where version_parent = null & game_type = {GAME_TYPES} "
            f"& themes != ({EROTIC_THEME}) & cover != null & first_release_date != null; "
            "limit 5;"
        ),
        timeout=25,
    )
    response.raise_for_status()

    wanted = normalise(name)
    for game in response.json():
        if EROTIC_THEME in (game.get("themes") or []):
            continue
        if normalise(game["name"]) == wanted:
            return game
    return None


def upload_cover(s3, bucket: str, public_url: str, game_id: str, image_id: str) -> str:
    url = f"https://images.igdb.com/igdb/image/upload/{COVER_SIZE}/{image_id}.jpg"
    image = requests.get(url, timeout=30)
    image.raise_for_status()

    key = f"covers/{game_id}.jpg"
    s3.put_object(
        Bucket=bucket,
        Key=key,
        Body=image.content,
        ContentType="image/jpeg",
        CacheControl="public, max-age=31536000, immutable",
    )
    return f"{public_url.rstrip('/')}/{key}"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true", help="actually write")
    parser.add_argument("--count", type=int, default=60, help="how many to add (default 60)")
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

    with psycopg.connect(os.environ["DATABASE_URL"]) as connection:
        with connection.cursor() as cursor:
            cursor.execute("SELECT game_name FROM gamebuddy.games")
            existing = {normalise(row[0]) for row in cursor.fetchall()}
        print(f"{len(existing)} games already in the catalogue.")

        candidate_ids = popular_game_ids(headers)
        print(f"{len(candidate_ids)} popular ids from IGDB; fetching details...")

        candidates = fetch_games(candidate_ids, headers)
        # IGDB returns records in whatever order it likes; restore the popularity order so
        # "the top N" means the most popular N rather than an arbitrary N.
        rank = {game_id: index for index, game_id in enumerate(candidate_ids)}
        candidates.sort(key=lambda g: rank.get(g["id"], 10**6))

        additions = []

        # The named ones first, so a full --count of popular titles can never crowd out
        # the games that were explicitly asked for.
        for name in ALWAYS_INCLUDE:
            if normalise(name) in existing:
                continue
            game = fetch_by_name(name, headers)
            if game is None:
                print(f"  !  {name}: not found on IGDB")
                continue
            existing.add(normalise(game["name"]))
            additions.append(game)

        for game in candidates:
            if len(additions) >= args.count:
                break
            key = normalise(game["name"])
            if key in existing or key in EXCLUDE_NAMES:
                continue
            existing.add(key)
            additions.append(game)

        print(f"{len(candidates)} passed the filters, {len(additions)} are new.\n")

        for game in additions:
            year = time.strftime("%Y", time.gmtime(game["first_release_date"]))
            print(f"  + {game['name'][:44]:46} {year}  {categorise(game)}")

        if not args.apply:
            print("\nDry run — nothing written. Re-run with --apply.")
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
            written += 1

        connection.commit()
        print(f"\nAdded {written} game(s).")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
