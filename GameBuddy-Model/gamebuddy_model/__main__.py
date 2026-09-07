"""Command line entry point.

Offline, on synthetic data — how the pipeline is tuned and tested, where the latent
state is known so the answers can be checked:

    python -m gamebuddy_model generate --n 20000 --out ./data
    python -m gamebuddy_model evaluate --n 6000

Against the live product — how the shipped artefact is built and kept current:

    python -m gamebuddy_model export --out ./data --database-url postgresql://...
    python -m gamebuddy_model train  --data ./data --out ./artifacts
    # then POST /admin/reload on the model API

Both paths write the same four CSVs, so ``train`` cannot tell them apart. That is
deliberate: it is what makes a result measured offline mean something in production.
"""

from __future__ import annotations

import argparse
import os
import pickle
import sys
import warnings
from pathlib import Path

warnings.filterwarnings("ignore", category=FutureWarning)


def _generate(args: argparse.Namespace) -> int:
    from .population import PopulationGenerator, summarise
    from .seed import write_csv, write_sql

    population = PopulationGenerator(n_gamers=args.n, seed=args.seed).generate()
    stats = summarise(population)

    out = Path(args.out)
    csv_paths = write_csv(population, out)
    sql_path = write_sql(population, out / "seed.sql", schema=args.schema)

    print(f"Generated {stats['gamers']:.0f} synthetic gamers, "
          f"{stats['exposed_pairs']:.0f} impressions, "
          f"{stats['mutual_matches']:.0f} mutual matches "
          f"(median {stats['median_matches_per_gamer']:.0f} each, "
          f"{stats['gamers_with_no_match']:.0f} with none).")
    print(f"  like rate        {stats['like_rate']:.3f}")
    print(f"  same-archetype   {stats['same_archetype']:.1%} of matches "
          f"(chance is {1 / 14:.1%})")
    # ASCII only in CLI output: the Windows console defaults to cp1252 and renders an
    # em-dash as a replacement character, which makes a clean run look like a failure.
    print(f"  warm/cold lift   {stats['warm_cold_lift']:.2f}x "
          f"- warm archetype pairs match this much more often than cold ones")
    if stats["under_age"]:
        print(f"ERROR: {stats['under_age']:.0f} generated gamers are under "
              "18, which the product does not allow", file=sys.stderr)
        return 1
    for name, path in csv_paths.items():
        print(f"  {name:<13} {path}")
    print(f"  {'seed sql':<13} {sql_path}  (schema: {args.schema})")
    return 0


def _evaluate(args: argparse.Namespace) -> int:
    from .evaluate import (RewrittenModel, compare, default_builders, split_exposures,
                           structure_report)
    from .population import PopulationGenerator, summarise

    population = PopulationGenerator(n_gamers=args.n, seed=args.seed).generate()
    split = split_exposures(population, seed=args.seed)
    stats = summarise(population)

    print(f"Population: {stats['gamers']:.0f} gamers, like rate {stats['like_rate']:.3f}, "
          f"{stats['mutual_matches']:.0f} mutual matches "
          f"(median {stats['median_matches_per_gamer']:.0f} each).")
    print(f"Structure:  {stats['same_archetype']:.1%} of matches are same-archetype, "
          f"warm archetype pairs match {stats['warm_cold_lift']:.2f}x as often as cold ones.")
    print()

    builders = default_builders(population, split, include_original=args.include_original)
    print(compare(population, builders, split).to_string())

    # What the shipped model actually serves, against the population's own figures above.
    served = structure_report(
        RewrittenModel(population, split, use_desirability=True), population
    )
    print()
    print(f"Hybrid serves: {served['same_archetype']:.1%} same-archetype, "
          f"mean pair warmth {served['mean_warmth']:.3f}, "
          f"mean vibe similarity {served['mean_vibe_similarity']:.3f}.")
    return 0


def _export(args: argparse.Namespace) -> int:
    from .export import connect, export

    database_url = args.database_url or os.environ.get("DATABASE_URL")
    if not database_url:
        print("No database URL. Pass --database-url or set DATABASE_URL.", file=sys.stderr)
        return 2

    with connect(database_url) as connection:
        paths = export(connection, args.out, schema=args.schema, include_bots=args.include_bots)

    rows = sum(1 for _ in paths["gamers"].open(encoding="utf-8")) - 1
    print(f"Exported {rows} gamers from {args.schema}"
          f"{' (including bot accounts)' if args.include_bots else ''}.")
    for name, path in paths.items():
        print(f"  {name:<13} {path}")
    print("\nNext: python -m gamebuddy_model train --data "
          f"{args.out} --out ./artifacts")
    return 0


def _train(args: argparse.Namespace) -> int:
    import csv as csv_module

    from .recommender import train

    from .seed import is_seed_account

    data = Path(args.data)
    gamers: list[str] = []
    # Platforms ride on gamers.csv rather than profiles.csv because that is the shape both
    # producers already emit: the synthetic exporter writes them pipe-joined, and the
    # Postgres exporter string_aggs them the same way.
    platforms: dict[str, list[str]] = {}
    # Which rows may be learned from but never recommended. Marked at training time from
    # the address, acted on at serving time — see Recommender.hidden.
    hidden: dict[str, bool] = {}
    with (data / "gamers.csv").open(encoding="utf-8") as fh:
        for row in csv_module.DictReader(fh):
            gamers.append(row["user_id"])
            platforms[row["user_id"]] = [p for p in (row.get("platforms") or "").split("|") if p]
            hidden[row["user_id"]] = is_seed_account(row.get("email"))

    games: dict[str, list[str]] = {uid: [] for uid in gamers}
    keywords: dict[str, list[str]] = {uid: [] for uid in gamers}
    with (data / "profiles.csv").open(encoding="utf-8") as fh:
        for row in csv_module.DictReader(fh):
            target = games if row["kind"] == "game" else keywords
            target.setdefault(row["user_id"], []).append(row["value"])

    model = train(
        gamers,
        [games[u] for u in gamers],
        [keywords[u] for u in gamers],
        [platforms[u] for u in gamers],
        hidden=[hidden[u] for u in gamers],
    )

    seeded = sum(hidden.values())
    if seeded:
        print(f"{seeded} of {len(gamers)} rows are seed accounts. They shape the feature "
              f"space either way; set HIDE_SEED_ACCOUNTS=true on the model service to stop "
              f"serving them, leaving {len(gamers) - seeded} recommendable.")

    likes_path = data / "interactions.csv"
    if likes_path.exists():
        likes: list[tuple[str, str]] = []
        # Every row, like or pass, is one impression of the target. Without this
        # denominator the prior would rank by reach instead of appeal.
        exposures: dict[str, int] = {}
        with likes_path.open(encoding="utf-8") as fh:
            for row in csv_module.DictReader(fh):
                exposures[row["target_id"]] = exposures.get(row["target_id"], 0) + 1
                if row["decision"] == "LIKE":
                    likes.append((row["user_id"], row["target_id"]))
        model.fit_desirability(likes, exposures)
        print(f"Fitted desirability prior from {len(likes)} likes "
              f"over {sum(exposures.values())} impressions.")

    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    artefact = out / "recommender.pkl"
    with artefact.open("wb") as fh:
        pickle.dump(model, fh, protocol=pickle.HIGHEST_PROTOCOL)

    r = model.report
    print(f"Trained on {r.n_gamers} gamers, {r.n_features} features, "
          f"{r.n_components} components ({r.explained_variance:.0%} variance), "
          f"k={r.chosen_k}, cluster sizes {r.cluster_sizes}")
    print(f"Wrote {artefact}")
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="gamebuddy_model")
    sub = parser.add_subparsers(dest="command", required=True)

    gen = sub.add_parser("generate", help="generate a synthetic population and export it")
    # 20,000 is where the scaling study flattens: the model's recovery of the latent
    # state reaches 0.303 there against 0.308 at fifty thousand, for a fifth of the
    # generation time and a quarter of the seed file. See the README.
    gen.add_argument("--n", type=int, default=20000)
    gen.add_argument("--seed", type=int, default=20260801)
    gen.add_argument("--out", default="./data")
    gen.add_argument("--schema", default="schtrain",
                     help="target schema; 'schauth' seeds the live tables — see seed.py")
    gen.set_defaults(func=_generate)

    ev = sub.add_parser("evaluate", help="score models against the match graph")
    ev.add_argument("--n", type=int, default=2000)
    ev.add_argument("--seed", type=int, default=20260801)
    ev.add_argument("--include-original", action="store_true",
                    help="also score the original pipeline (slow: O(n^2) correlation)")
    ev.set_defaults(func=_evaluate)

    ex = sub.add_parser("export", help="export live gamers and swipes from Postgres to CSV")
    ex.add_argument("--out", default="./data")
    ex.add_argument("--database-url", default=None,
                    help="Postgres URL; falls back to $DATABASE_URL")
    ex.add_argument("--schema", default="gamebuddy")
    ex.add_argument("--include-bots", action="store_true",
                    help="also export synthetic seed accounts, which are excluded by default")
    ex.set_defaults(func=_export)

    tr = sub.add_parser("train", help="train and pickle a recommender from exported CSV")
    tr.add_argument("--data", default="./data")
    tr.add_argument("--out", default="./artifacts")
    tr.set_defaults(func=_train)

    args = parser.parse_args(argv)
    return int(args.func(args))


if __name__ == "__main__":
    raise SystemExit(main())
