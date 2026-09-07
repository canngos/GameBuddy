"""Records which library versions built an artefact, and checks them when it is loaded.

The artefact is a pickle of fitted scikit-learn estimators. Unpickling one written by a
different version of scikit-learn or numpy is undefined behaviour, and the failure mode is
the bad one: not an exception, but a model that loads cleanly, answers every request and
ranks wrongly. Nothing in the response looks different — it is a list of plausible user
ids either way — so a version drift can sit in production indefinitely.

The gap is real rather than hypothetical here. Training runs on a developer machine and
the API runs in a container built from a different base image; at the time of writing
those were Python 3.14 and Python 3.12. Exact pins in ``requirements.txt`` are the fix for
the libraries; this is the check that says so out loud when someone retrains outside the
pinned environment, or ships an artefact built before the pins were raised.

Deliberately a warning and not a refusal. A version mismatch is *usually* harmless, and an
API that refuses to start because a patch release moved is worse than one that serves and
says so — the model going down takes the whole feed with it.
"""

from __future__ import annotations

import platform
from dataclasses import dataclass, field


def _library_versions() -> dict[str, str]:
    import numpy
    import scipy
    import sklearn

    return {
        "python": platform.python_version(),
        "numpy": numpy.__version__,
        "scipy": scipy.__version__,
        "scikit-learn": sklearn.__version__,
    }


@dataclass
class BuildInfo:
    """The environment an artefact was trained in."""

    versions: dict[str, str] = field(default_factory=_library_versions)

    def mismatches(self) -> dict[str, tuple[str, str]]:
        """``{library: (trained_with, running_with)}`` for the libraries that differ.

        Python itself is excluded — see ``interpreter_mismatch``. A check that fires on
        every boot for an expected condition is one people learn to scroll past, and then
        it is not a check.
        """
        current = _library_versions()
        return {
            name: (trained, current[name])
            for name, trained in self.versions.items()
            if name != "python" and name in current and current[name] != trained
        }

    def interpreter_mismatch(self) -> tuple[str, str] | None:
        """``(trained_with, running_with)`` if the Python version differs.

        Reported separately and less loudly than a library mismatch, because it is the
        normal state of this project — training runs on a developer machine and the API
        runs in a container built from a different base image — and because the pickle
        protocol is stable across the versions involved. What is actually *inside* the
        artefact is scikit-learn, numpy and scipy objects, and those are pinned exactly.
        Worth stating at startup so it is on the record, not worth an alarm.
        """
        trained = self.versions.get("python")
        running = platform.python_version()
        return (trained, running) if trained and trained != running else None

    def describe(self) -> str:
        return ", ".join(f"{name} {value}" for name, value in sorted(self.versions.items()))
