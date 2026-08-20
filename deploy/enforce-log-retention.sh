#!/usr/bin/env bash
#
# Seven-day retention for the two logs that rotation alone will not bound.
#
# PRIVACY.md section 9 promises seven days for server logs, and three things write them:
#
#   1. the backend's own file log — `LOG_MAX_HISTORY: 7` in docker-compose.prod.yml, which
#      Logback reads as a number of days. Genuinely time-based. Nothing to do here.
#   2. Caddy's access log, which holds a client IP against every request.
#   3. every container's console output, captured by Docker's json-file driver.
#
# **Why 2 and 3 are not solved by their own rotation settings.** Both rotate by *size*:
# Caddy at `roll_size 10MB`, Docker at `max-size: 10m`. At closed-testing volume neither
# reaches that for months, so the active file is never rotated and the entries inside it —
# including IP addresses from the first week of testing — are still there in the spring.
# Caddy's `roll_keep_for 168h` only governs files that have already rolled; it cannot expire
# the one still being written to.
#
# **And why the obvious fix does not work.** The first version of this script was:
#
#     find /var/lib/docker/containers -name '*-json.log' -mtime +7 -exec truncate -s 0 {} \;
#
# which never fires. `-mtime` is the *modification* time, and a log being appended to has a
# modification time of a few seconds ago, permanently. The file is old; its mtime never is.
# So the age has to be read from the content — the first line of each of these formats
# carries the timestamp of the oldest entry still in it.
#
# Truncate rather than delete: the file is an open handle held by Docker or Caddy, and
# unlinking it leaves the writer appending to an inode nothing can read. `truncate -s 0`
# frees the space and leaves the handle valid.
#
# Six days, not seven, because this runs nightly: checking at six guarantees nothing is ever
# found at seven. Truncating loses the recent end of the log as well as the old end, which is
# the cost of this approach — it is why the threshold is not lower, and why the backend's own
# log (structured, seven days, rotated properly by Logback) is the one to read when
# investigating something.
#
# Runs from root's crontab; /var/lib/docker is root-owned. See DEPLOY.md section 7.

set -euo pipefail

THRESHOLD_DAYS=6
CUTOFF=$(date -d "$THRESHOLD_DAYS days ago" +%s)

truncated=0

# Caddy writes JSON lines whose `ts` is an epoch float: {"level":"info","ts":1755600000.12,…}
caddy_oldest() {
  head -n 1 "$1" 2>/dev/null | grep -o '"ts":[0-9]*' | head -n 1 | cut -d: -f2
}

# Docker writes JSON lines whose `time` is RFC 3339: {"log":"…","time":"2026-08-13T03:12:00Z"}
docker_oldest() {
  local stamp
  stamp=$(head -n 1 "$1" 2>/dev/null | grep -o '"time":"[^"]*"' | head -n 1 | cut -d'"' -f4)
  [ -n "$stamp" ] && date -d "$stamp" +%s 2>/dev/null
}

# Truncates $1 when the oldest entry in it predates the cutoff.
expire() {
  local file=$1 oldest=$2
  [ -s "$file" ] || return 0
  [ -n "$oldest" ] || return 0
  if [ "$oldest" -lt "$CUTOFF" ]; then
    truncate -s 0 -- "$file"
    truncated=$((truncated + 1))
    echo "truncated $file (oldest entry $(date -d "@$oldest" +%F))"
  fi
}

# 1. Caddy's access log, wherever the named volume actually lives.
#
# Every lookup below tolerates absence. This script must not fail the cron job on a box
# where Caddy has not started yet or a path does not exist — a retention pass that exits 1
# is a retention pass somebody eventually stops running.
volume=$(docker volume ls -q --filter name=caddy-data 2>/dev/null | head -n 1 || true)
if [ -n "$volume" ]; then
  data=$(docker volume inspect -f '{{ .Mountpoint }}' "$volume" 2>/dev/null || true)
  [ -n "$data" ] || data=/nonexistent
  access="$data/access.log"
  if [ -f "$access" ]; then expire "$access" "$(caddy_oldest "$access")"; fi
  # Files Caddy has already rolled. `roll_keep_for 168h` should have taken these; this is
  # the belt to its braces, and costs nothing when there is nothing to find.
  find "$data" -maxdepth 1 -name 'access*.log*' ! -name 'access.log' -mtime +7 -delete 2>/dev/null || true
fi

# 2. Every container's console output.
while IFS= read -r log; do
  expire "$log" "$(docker_oldest "$log")"
done < <(find /var/lib/docker/containers -maxdepth 2 -name '*-json.log' 2>/dev/null || true)

# Files Docker has already rotated are never appended to again, so mtime is honest for these.
find /var/lib/docker/containers -maxdepth 2 -name '*-json.log.*' -mtime +7 -delete 2>/dev/null || true

echo "[logs] retention pass complete, $truncated file(s) truncated"
