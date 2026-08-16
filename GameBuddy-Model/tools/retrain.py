"""The nightly retrain: live database → artefact → serving, with the guards that matter.

Why this is a script and not three commands in a cron line
---------------------------------------------------------
``export``, ``train`` and ``POST /admin/reload`` already exist and each does its job. The
part that needs writing down is what happens when one of them goes wrong at three in the
morning, because the failure modes are quiet ones:

* **Too little data.** A migration holds a lock, the export returns four hundred gamers
  instead of forty thousand, training succeeds — and a perfectly valid artefact that knows
  almost nobody replaces one that knew everybody. Every deck goes thin overnight and
  nothing errors. Guarded by ``--min-gamers`` and by a floor relative to the artefact
  already serving.
* **A broken artefact.** Training writes a file, the file is corrupt or the model answers
  nothing. Swapping it in takes the feed down. Guarded by loading the candidate and asking
  it real questions *before* it goes anywhere near the serving path.
* **A half-written file.** The service reloads mid-write and unpickles a truncated
  artefact. Guarded by writing to a temporary name and renaming — ``os.replace`` is atomic
  within a filesystem.
* **A bad artefact that passed the checks anyway.** The previous one is kept next to it,
  so rolling back is a rename rather than a retrain.

The failure posture throughout: **leave the old artefact serving**. A model a day out of
date is a good product; no model is an error page. So every check below exits non-zero
without touching what is live, and the exit code is what a scheduler should alert on.
"""

from __future__ import annotations

import argparse
import csv
import os
import pickle
import shutil
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
from pathlib import Path

#: Below this the population is too small to learn anything and almost certainly signals a
#: broken export rather than a small product. The recommender itself copes with far fewer —
#: it falls back to a single cluster — but an artefact this size replacing a real one is
#: the outcome being prevented.
MIN_GAMERS = 500

#: A retrain that finds less than this share of the currently-served population is treated
#: as a bad export rather than as churn. Real attrition does not remove a third of the
#: accounts overnight; a half-applied migration or a mis-set schema does.
MIN_SHARE_OF_CURRENT = 0.7


def log(message: str) -> None:
    print(f"[retrain] {message}", flush=True)


def die(message: str) -> None:
    print(f"[retrain] FAILED: {message}", file=sys.stderr, flush=True)
    print("[retrain] the artefact already in place is untouched and still serving",
          file=sys.stderr, flush=True)
    raise SystemExit(1)


def run(args: list[str], **kwargs) -> None:
    log("$ " + " ".join(args))
    result = subprocess.run(args, **kwargs)
    if result.returncode != 0:
        die(f"{args[0]} {args[1] if len(args) > 1 else ''} exited {result.returncode}")


def count_rows(path: Path) -> int:
    with path.open(encoding="utf-8") as fh:
        return max(0, sum(1 for _ in fh) - 1)


def gamers_in(artefact: Path) -> int:
    """How many gamers the artefact currently in place knows about, or 0 if there is none."""
    if not artefact.exists():
        return 0
    try:
        with artefact.open("rb") as fh:
            return len(pickle.load(fh).user_ids)
    except Exception as exc:  # noqa: BLE001 - a corrupt current artefact must not stop a retrain
        log(f"could not read the current artefact ({exc}); treating as empty")
        return 0


def verify(candidate: Path, expected_gamers: int) -> None:
    """Loads the new artefact and asks it the questions the service will ask.

    Training can succeed and still produce something useless — a feature space with an
    empty vocabulary, a cluster assignment that puts everyone in one bucket, a ranking
    that returns nothing. None of that raises at training time. It is much cheaper to find
    out here than from the first user whose deck comes back empty.
    """
    try:
        with candidate.open("rb") as fh:
            model = pickle.load(fh)
    except Exception as exc:  # noqa: BLE001
        die(f"the new artefact does not unpickle: {exc}")

    if len(model.user_ids) != expected_gamers:
        die(f"artefact holds {len(model.user_ids)} gamers, expected {expected_gamers}")
    if model.space.n_features < 10:
        die(f"feature space has only {model.space.n_features} features; the catalogue "
            "probably failed to load")

    # Every probe below runs inside one guard, because a malformed artefact fails in
    # whatever way its particular corruption dictates — a shape mismatch here raised
    # ValueError out of numpy rather than returning an empty list. What matters is that
    # anything other than a clean answer stops the job with a legible reason instead of a
    # traceback at 3am; the artefact in place is untouched either way.
    try:
        sample = model.user_ids[:: max(1, len(model.user_ids) // 25)][:25]
        empty = [uid for uid in sample if not model.similar_to(uid, 50)]
        if empty:
            die(f"{len(empty)} of {len(sample)} sampled gamers rank nothing")

        # The cold-start path is what every new signup uses until the next run, so a
        # retrain that breaks it breaks the experience of precisely the users who just
        # arrived.
        probe = model.similar_to_profile(
            ["Valorant", "Counter-Strike 2"], ["competitive", "tryhard"], 20,
            platforms=["PC"])
        if not probe:
            die("cold start returns nothing for a well-formed profile")
    except SystemExit:
        raise
    except Exception as exc:  # noqa: BLE001
        die(f"the new artefact loaded but could not answer a query: {exc!r}")

    log(f"verified: {len(model.user_ids)} gamers, {model.space.n_features} features, "
        f"k={model.report.chosen_k}, cold start OK")


def reload_service(url: str, api_key: str, retries: int = 3) -> None:
    """Asks the service to pick the new artefact up.

    Retried, and a failure here is *not* fatal: the file is already in place, so the next
    restart or the next successful reload serves it. Killing the job at this point would
    report a failed retrain when the retrain in fact succeeded.
    """
    request = urllib.request.Request(
        url.rstrip("/") + "/admin/reload", data=b"",
        headers={"X-Internal-Api-Key": api_key}, method="POST")
    for attempt in range(1, retries + 1):
        try:
            with urllib.request.urlopen(request, timeout=120) as response:
                log(f"reloaded: {response.read().decode()[:120]}")
                return
        except (urllib.error.URLError, OSError) as exc:
            log(f"reload attempt {attempt}/{retries} failed: {exc}")
            if attempt < retries:
                time.sleep(5 * attempt)
    log("WARNING: could not reload. The new artefact is in place and will be picked up on "
        "the next restart; serving continues on the old one until then.")


def seconds_until(clock_time: str, timezone: str) -> float:
    """Seconds from now until the next occurrence of ``HH:MM`` in ``timezone``.

    Computed against a real timezone rather than by sleeping a fixed 86400 seconds from
    the last run. A fixed interval drifts by however long each run took, so a job that
    starts at midnight and takes four minutes is running at ten past by the end of the
    month; and across a DST boundary it lands an hour out and stays there.
    """
    from datetime import datetime, timedelta
    from zoneinfo import ZoneInfo

    zone = ZoneInfo(timezone)
    hour, minute = (int(part) for part in clock_time.split(":"))
    now = datetime.now(zone)
    target = now.replace(hour=hour, minute=minute, second=0, microsecond=0)
    if target <= now:
        target += timedelta(days=1)
    return (target - now).total_seconds()


def serve_schedule(args: argparse.Namespace) -> int:
    """Runs the retrain once per day, forever.

    A loop in the container rather than a crontab because the image has no cron daemon and
    adding one means running something as root in a container that deliberately does not.
    On Kubernetes use the CronJob in ``k8s/model-retrain-cronjob.yaml`` instead and drop
    ``--schedule`` — a scheduler that a supervisor already provides is not worth
    reimplementing.

    A failed run is logged and the loop continues. The alternative — exiting — turns one
    bad night into a permanently stale model, which is the larger of the two problems.
    """
    log(f"scheduled: every day at {args.schedule} {args.timezone}")
    while True:
        wait = seconds_until(args.schedule, args.timezone)
        log(f"next run in {wait / 3600:.1f}h")
        time.sleep(wait)
        try:
            retrain_once(args)
        except SystemExit as exc:
            log(f"run failed (exit {exc.code}); the old artefact is still serving, "
                "retrying tomorrow")
        except Exception as exc:  # noqa: BLE001
            log(f"run crashed: {exc!r}; the old artefact is still serving")
        # A run that finishes in under a minute would otherwise re-trigger on the same
        # tick, because the target time has not yet passed.
        time.sleep(61)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        # Not __doc__: that is thirty lines of rationale for whoever opens the file,
        # and ASCII-only because argparse writes it straight to a console that on
        # Windows is cp1252 and raises UnicodeEncodeError on an arrow or an em-dash.
        description="Retrain the recommender from live data and reload the service.")
    parser.add_argument("--artifacts", default=os.environ.get("ARTIFACT_DIR", "./artifacts"))
    parser.add_argument("--database-url", default=os.environ.get("DATABASE_URL"))
    parser.add_argument("--schema", default=os.environ.get("DB_SCHEMA", "gamebuddy"))
    parser.add_argument("--model-url", default=os.environ.get("PREDICT_SERVICE_URL",
                                                              "http://model:8000"))
    parser.add_argument("--api-key", default=os.environ.get("INTERNAL_API_KEY", ""))
    parser.add_argument("--min-gamers", type=int, default=MIN_GAMERS)
    parser.add_argument("--include-bots", action="store_true",
                        default=os.environ.get("RETRAIN_INCLUDE_BOTS", "").lower()
                        in {"1", "true", "yes"},
                        help="train on synthetic seed accounts too. Off by default, but "
                             "correct while the seeded population IS the candidate pool — "
                             "see RETRAIN_INCLUDE_BOTS in docker-compose.yml")
    parser.add_argument("--keep", type=int, default=7,
                        help="how many previous artefacts to retain for rollback")
    parser.add_argument("--schedule", default=None, metavar="HH:MM",
                        help="stay running and retrain daily at this local time")
    parser.add_argument("--timezone", default=os.environ.get("RETRAIN_TZ", "UTC"),
                        help="timezone for --schedule (default UTC)")
    args = parser.parse_args(argv)

    if not args.database_url:
        die("no database URL. Set DATABASE_URL or pass --database-url.")

    if args.schedule:
        return serve_schedule(args)
    return retrain_once(args)


def retrain_once(args: argparse.Namespace) -> int:
    artefact_dir = Path(args.artifacts)
    artefact_dir.mkdir(parents=True, exist_ok=True)
    live = artefact_dir / "recommender.pkl"
    currently_serving = gamers_in(live)
    started = time.time()

    with tempfile.TemporaryDirectory(prefix="gamebuddy-retrain-") as tmp:
        data, out = Path(tmp) / "data", Path(tmp) / "out"

        export_cmd = [sys.executable, "-m", "gamebuddy_model", "export",
                      "--out", str(data), "--database-url", args.database_url,
                      "--schema", args.schema]
        if args.include_bots:
            export_cmd.append("--include-bots")
        run(export_cmd)

        exported = count_rows(data / "gamers.csv")
        log(f"exported {exported} gamers (currently serving {currently_serving})")

        if exported < args.min_gamers:
            die(f"only {exported} gamers exported, below the floor of {args.min_gamers}. "
                "This is far more likely to be a broken export than a shrunken product.")
        if currently_serving and exported < currently_serving * MIN_SHARE_OF_CURRENT:
            die(f"exported {exported} gamers against {currently_serving} currently served "
                f"({exported / currently_serving:.0%}). Real churn does not look like this.")

        run([sys.executable, "-m", "gamebuddy_model", "train",
             "--data", str(data), "--out", str(out)])

        candidate = out / "recommender.pkl"
        if not candidate.exists():
            die("training reported success but wrote no artefact")
        verify(candidate, exported)

        # Keep the outgoing artefact before anything replaces it.
        if live.exists():
            stamp = time.strftime("%Y%m%d-%H%M%S")
            shutil.copy2(live, artefact_dir / f"recommender-{stamp}.pkl")

        # Same directory, so os.replace is atomic: readers see either the whole old file
        # or the whole new one, never a partial write.
        staged = artefact_dir / "recommender.pkl.incoming"
        shutil.copy2(candidate, staged)
        os.replace(staged, live)
        log(f"swapped in {live} ({live.stat().st_size / 1e6:.1f} MB)")

    previous = sorted(artefact_dir.glob("recommender-*.pkl"))
    for stale in previous[:-args.keep] if args.keep else previous:
        stale.unlink()
        log(f"pruned {stale.name}")

    if args.api_key:
        reload_service(args.model_url, args.api_key)
    else:
        log("no INTERNAL_API_KEY; skipping reload. The artefact is in place for the next "
            "restart.")

    log(f"done in {time.time() - started:.0f}s")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
