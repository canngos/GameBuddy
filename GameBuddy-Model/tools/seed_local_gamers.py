"""Seeds swipeable gamers into the LOCAL database so the deck has a pool to show.

Deliberately not `gamebuddy_model.seed.write_sql`: that generates its own game and
keyword ids from name hashes, which would insert a second, parallel catalogue alongside
the one `db/seed-local.sql` already loaded. Here the population is generated the same
way, but resolved against the ids already in the database, so the joins point at the
real rows and the recommender sees the features it was trained on.

Every account carries the `@bot.gamebuddy.invalid` marker from the model package, so
`DELETE FROM gamebuddy.gamer WHERE email LIKE '%@bot.gamebuddy.invalid'` removes all of
it. `.invalid` is reserved by RFC 2606 and can never be a real address, and the password
is not a valid bcrypt hash so none of these can be logged into.

LOCAL ONLY. These are fixtures, not users.
"""

from __future__ import annotations

import sys
from pathlib import Path

# Runnable from anywhere: the model package sits one directory up.
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from gamebuddy_model.population import PopulationGenerator  # noqa: E402
from gamebuddy_model.seed import BOT_EMAIL_DOMAIN, UNUSABLE_PASSWORD  # noqa: E402

#: Deliberately large. The recommender ranks against the population it was *trained*
#: on, and only the ids that also exist in this database survive `findAllById`. Seed a
#: few dozen and the intersection with the model's top-ranked candidates is nearly
#: empty, so the deck looks broken when it is merely under-populated. At 1200 the feed
#: comes back with a few dozen candidates, which is enough to work with.
N_GAMERS = 1200
SCHEMA = "gamebuddy"


def sql_str(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


def gender_char(value: str) -> str:
    """The population uses words; the column and the app both expect one character."""
    first = (value or "").strip()[:1].upper()
    return first if first in {"M", "F"} else "O"


def main() -> None:
    population = PopulationGenerator(n_gamers=N_GAMERS, seed=20260803).generate()

    out: list[str] = [
        "-- Local-only swipeable population. Remove with:",
        f"--   DELETE FROM {SCHEMA}.gamer WHERE email LIKE '%@{BOT_EMAIL_DOMAIN}';",
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
        out.append(
            f"INSERT INTO {SCHEMA}.gamer "
            "(user_id, username, email, age, country, gender, pwd, is_blocked, "
            " is_verified, is_registered, coin, role, version, "
            " accepts_used, swipes_used, subscription_tier, last_modified_date) VALUES ("
            f"{sql_str(g.user_id)}, {sql_str(g.username)}, {sql_str(g.email)}, {g.age}, "
            f"{sql_str(g.country)}, {sql_str(gender_char(g.gender))}, "
            f"{sql_str(UNUSABLE_PASSWORD)}, "
            "FALSE, TRUE, TRUE, 0, 'USER', 0, "
            "0, 0, 'BASIC', NOW()) ON CONFLICT (user_id) DO NOTHING;"
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

    out += ["", "COMMIT;"]

    target = Path(__file__).with_name("local-gamers.sql")
    target.write_text("\n".join(out), encoding="utf-8")
    adults = sum(1 for g in population.gamers if g.age >= 18)
    print(f"wrote {target} — {len(population.gamers)} gamers ({adults} adult, "
          f"{len(population.gamers) - adults} minor)")


if __name__ == "__main__":
    main()
