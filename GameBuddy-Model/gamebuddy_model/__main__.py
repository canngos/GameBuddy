"""Command line entry point.

    python -m gamebuddy_model generate --n 4000 --out ./data
    python -m gamebuddy_model evaluate --n 2000
    python -m gamebuddy_model train --data ./data --out ./artifacts
"""

from __future__ import annotations

import argparse
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

    print(f"Generated {stats['gamers']:.0f} synthetic gamers "
          f"({stats['minors']:.0f} minors, {stats['mutual_matches']:.0f} mutual matches, "
          f"median {stats['median_matches_per_gamer']:.0f} each).")
    if stats["cross_band_matches"]:
        print("ERROR: cross-age-band matches present", file=sys.stderr)
        return 1
    print("No minor/adult matches, as required.")
    for name, path in csv_paths.items():
        print(f"  {name:<13} {path}")
    print(f"  {'seed sql':<13} {sql_path}  (schema: {args.schema})")
    return 0


def _evaluate(args: argparse.Namespace) -> int:
    from .evaluate import (OriginalPipeline, PopularityBaseline, RandomBaseline,
                           RewrittenModel, compare)
    from .population import PopulationGenerator

    population = PopulationGenerator(n_gamers=args.n, seed=args.seed).generate()
    builders = {
        "random": lambda: RandomBaseline(population),
        "popularity": lambda: PopularityBaseline(population),
        "rewrite: content only": lambda: RewrittenModel(population),
        "rewrite: hybrid": lambda: RewrittenModel(population, use_desirability=True),
    }
    if args.include_original:
        builders["original (as-is)"] = lambda: OriginalPipeline(population)

    print(compare(population, builders).to_string())
    return 0


def _train(args: argparse.Namespace) -> int:
    import csv as csv_module

    from .recommender import train

    data = Path(args.data)
    gamers: list[str] = []
    with (data / "gamers.csv").open(encoding="utf-8") as fh:
        for row in csv_module.DictReader(fh):
            gamers.append(row["user_id"])

    games: dict[str, list[str]] = {uid: [] for uid in gamers}
    keywords: dict[str, list[str]] = {uid: [] for uid in gamers}
    with (data / "profiles.csv").open(encoding="utf-8") as fh:
        for row in csv_module.DictReader(fh):
            target = games if row["kind"] == "game" else keywords
            target.setdefault(row["user_id"], []).append(row["value"])

    model = train(gamers, [games[u] for u in gamers], [keywords[u] for u in gamers])

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
    gen.add_argument("--n", type=int, default=4000)
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

    tr = sub.add_parser("train", help="train and pickle a recommender from exported CSV")
    tr.add_argument("--data", default="./data")
    tr.add_argument("--out", default="./artifacts")
    tr.set_defaults(func=_train)

    args = parser.parse_args(argv)
    return int(args.func(args))


if __name__ == "__main__":
    raise SystemExit(main())
