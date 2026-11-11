"""Next calendar version YYYY.M.N of Groma, as in nemeton.

Year and month (no leading zero) of the current UTC day; N restarts at 1 each
month and follows the highest existing tag vYYYY.M.N of that month. Prints
GitHub Actions outputs:

    version=2026.11.1
    publiable=true|false

``publiable`` is true when a releasable commit (feat, fix, perf, revert, or a
breaking change) follows the last v* tag; release-please then opens or
updates the release PR with ``release-as: version``.
"""

from __future__ import annotations

import datetime as dt
import re
import subprocess

PUBLIABLE = re.compile(r"^(feat|fix|perf|revert)(\([^)]*\))?!?:|^[a-z]+(\([^)]*\))?!:|^BREAKING[ -]CHANGE:", re.M)


def version_suivante(tags: list[str], jour: dt.date) -> str:
    prefixe = f"v{jour.year}.{jour.month}."
    numeros = [int(t[len(prefixe):]) for t in tags if t.startswith(prefixe) and t[len(prefixe):].isdigit()]
    return f"{jour.year}.{jour.month}.{max(numeros, default=0) + 1}"


def publiable(messages: str) -> bool:
    return bool(PUBLIABLE.search(messages))


def _git(*args: str) -> str:
    return subprocess.run(["git", *args], check=True, capture_output=True, text=True).stdout


def main() -> None:
    _git("fetch", "--tags", "--force", "--quiet")
    tags = _git("tag", "--list", "v*").split()
    try:
        dernier = _git("describe", "--tags", "--abbrev=0", "--match", "v*").strip()
        plage = [f"{dernier}..HEAD"]
    except subprocess.CalledProcessError:
        plage = ["HEAD"]
    messages = _git("log", "--format=%B", *plage)
    print(f"version={version_suivante(tags, dt.datetime.now(dt.timezone.utc).date())}")
    print(f"publiable={'true' if publiable(messages) else 'false'}")


if __name__ == "__main__":
    main()
