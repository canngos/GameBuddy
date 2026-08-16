"""Seeds swipeable gamers into the LOCAL database so the deck has a pool to show.

Deliberately not `gamebuddy_model.seed.write_sql`: that generates its own game and
keyword ids from name hashes, which would insert a second, parallel catalogue alongside
the one `db/seed-local.sql` already loaded. Here the population is generated the same
way, but resolved against the ids already in the database, so the joins point at the
real rows and the recommender sees the features it was trained on.

The seed and the artefact have to agree
---------------------------------------
`/predict` ranks over the ids baked into the artefact, and the backend then keeps only
the ones its own database knows about. So the deck a gamer actually sees is the
*intersection* of the two, and if they were built from different populations that
intersection is empty.

That is not hypothetical — it is what this file used to do. It seeded 1,200 gamers on
seed 20260803 while the shipped artefact was trained on a different population entirely;
the model would return its 150 best candidates, essentially none of them would exist
locally, and the feed came back empty in a way that looks exactly like a broken
recommender.

The generator draws gamers in ordinal order from one RNG stream, so the first N of a
larger population are identical to a population of N. Keeping SEED equal to the artefact's
training seed therefore makes every gamer here a subset of the artefact's, whatever N is.
Do not change SEED without retraining, and do not retrain on a different seed without
changing it here.

Every account carries the `@bot.gamebuddy.invalid` marker from the model package, so
`DELETE FROM gamebuddy.gamer WHERE email LIKE '%@bot.gamebuddy.invalid'` removes all of
it. `.invalid` is reserved by RFC 2606 and can never be a real address, and the password
is not a valid bcrypt hash so none of these can be logged into.

LOCAL ONLY. These are fixtures, not users.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

# Runnable from anywhere: the model package sits one directory up.
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from gamebuddy_model.population import PopulationGenerator  # noqa: E402
from gamebuddy_model.seed import BOT_EMAIL_DOMAIN, UNUSABLE_PASSWORD  # noqa: E402

#: Must equal the seed the shipped artefact was trained on — see the module docstring.
#: This is the default of `python -m gamebuddy_model generate`.
SEED = 20260801

#: Seed the *whole* population the artefact was trained on, not a sample of it.
#:
#: Two reasons, and the second only became true once a nightly retrain existed.
#:
#: The deck is the intersection of what the model ranks and what the database holds, so
#: seeding a quarter of the artefact's population meant three out of four ranked
#: candidates did not exist locally and were dropped — a request for 150 came back with 33
#: usable.
#:
#: And the retrain reads the database, which makes the database the source of truth. Train
#: on 20,000 while seeding 5,000 and the next export finds a quarter of the population it
#: is replacing, which is indistinguishable from a broken export — `retrain.py` refuses it,
#: correctly, and would refuse it every night forever.
#:
#: The cost is a ~60 MB SQL file that takes about a minute to load. Worth it.
DEFAULT_GAMERS = 20000
SCHEMA = "gamebuddy"


def sql_str(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


def gender_char(value: str) -> str:
    """The population uses words; the column and the app both expect one character."""
    first = (value or "").strip()[:1].upper()
    return first if first in {"M", "F"} else "O"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--n", type=int, default=DEFAULT_GAMERS,
                        help=f"gamers to seed (default {DEFAULT_GAMERS})")
    parser.add_argument("--seed", type=int, default=SEED,
                        help="must match the artefact's training seed")
    args = parser.parse_args()

    population = PopulationGenerator(n_gamers=args.n, seed=args.seed).generate()

    out: list[str] = [
        "-- Local-only swipeable population. Remove with:",
        f"--   DELETE FROM {SCHEMA}.gamer WHERE email LIKE '%@{BOT_EMAIL_DOMAIN}';",
        f"-- Generated from seed {args.seed}, which must match the trained artefact.",
        "BEGIN;",
        "",
        "-- Resolve the catalogue by NAME against whatever ids this database already has.",
        "CREATE TEMP TABLE game_ids AS SELECT game_name, game_id FROM gamebuddy.games;",
        "CREATE TEMP TABLE keyword_ids AS SELECT keyword_name, id FROM gamebuddy.keywords;",
        "",
    ]

    for g in population.gamers:
        # Every NOT NULL column without a default has to be supplied explicitly:
        # accepts_used, swipes_used, subscription_tier and last_modified_date are all
        # managed by the application at runtime and have no DDL default.
        #
        # last_active_at is relative to NOW() so the product's "online now" filter (a
        # 15-minute window) has something to find whenever the seed is loaded. About a
        # tenth of the population lands inside it.
        out.append(
            f"INSERT INTO {SCHEMA}.gamer "
            "(user_id, username, email, age, country, gender, pwd, is_blocked, "
            " is_verified, is_registered, coin, role, version, "
            " accepts_used, swipes_used, subscription_tier, last_modified_date, "
            " last_active_at) VALUES ("
            f"{sql_str(g.user_id)}, {sql_str(g.username)}, {sql_str(g.email)}, {g.age}, "
            f"{sql_str(g.country)}, {sql_str(gender_char(g.gender))}, "
            f"{sql_str(UNUSABLE_PASSWORD)}, "
            "FALSE, TRUE, TRUE, 0, 'USER', 0, "
            "0, 0, 'BASIC', NOW(), "
            f"NOW() - INTERVAL '{g.last_active_minutes_ago} minutes') "
            "ON CONFLICT (user_id) DO NOTHING;"
        )

    out.append("")
    for g in population.gamers:
        for game in g.games:
            out.append(
                f"INSERT INTO {SCHEMA}.gamer_games_join (gamer_id, game_id) "
                f"SELECT {sql_str(g.user_id)}, game_id FROM game_ids "
                f"WHERE game_name = {sql_str(game)} ON CONFLICT DO NOTHING;"
            )
        for kw in g.keywords:
            out.append(
                f"INSERT INTO {SCHEMA}.gamer_keywords_join (gamer_id, keyword_id) "
                f"SELECT {sql_str(g.user_id)}, id FROM keyword_ids "
                f"WHERE keyword_name = {sql_str(kw)} ON CONFLICT DO NOTHING;"
            )
        # Without these every bot has an empty platform set, which passes the platform
        # filter unconditionally — so a Gold user filtering to "PlayStation" would get a
        # deck of PC players and no way to tell the filter was doing nothing.
        for platform in g.platforms:
            out.append(
                f"INSERT INTO {SCHEMA}.gamer_platform (user_id, platform) VALUES "
                f"({sql_str(g.user_id)}, {sql_str(platform)}) ON CONFLICT DO NOTHING;"
            )

    out += ["", "COMMIT;"]

    target = Path(__file__).with_name("local-gamers.sql")
    target.write_text("\n".join(out), encoding="utf-8")
    online = sum(1 for g in population.gamers if g.last_active_minutes_ago < 15)
    # ASCII only: the Windows console is cp1252 by default and mangles an em-dash.
    print(f"wrote {target} - {len(population.gamers)} gamers from seed {args.seed} "
          f"({online} currently inside the 15-minute 'online now' window)")
    print("Load with:")
    print(f"  docker compose exec -T postgres psql -U gamebuddy -d gamebuddy < {target.name}")


if __name__ == "__main__":
    main()
