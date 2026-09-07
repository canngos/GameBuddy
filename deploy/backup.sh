#!/usr/bin/env bash
#
# Nightly database backup, and the retention that makes the privacy policy true.
#
# Installed on the server as /home/gamebuddy/backup.sh and run from cron at 03:30 — see
# DEPLOY.md section 7. It lives here rather than only in that document because the two
# prunes below are a published commitment: PRIVACY.md section 9 tells account holders that
# "the database is backed up nightly, and each backup is destroyed seven days after it is
# taken". A retention rule nobody can find in the repository is a retention rule that gets
# quietly dropped the next time somebody rewrites this script from memory.
#
# The remote copies are pruned twice, on purpose:
#
#   - here, so the seven days is visible to anyone reading the code, and
#   - by an object lifecycle rule on the bucket itself, because this script only prunes on
#     the nights it actually runs. Cron dies, disks fill, servers get rebuilt — and the
#     failure mode of relying on this line alone is silent: backups simply accumulate, and
#     the policy becomes false without anything breaking.
#
# The lifecycle rule is the guarantee. This is the documentation.

set -euo pipefail

cd /home/gamebuddy/gamebuddy

STAMP=$(date +%F-%H%M)
DUMP="/home/gamebuddy/backups/gamebuddy-$STAMP.dump"

docker compose -f docker-compose.yml -f docker-compose.prod.yml exec -T postgres \
  pg_dump -U gamebuddy -d gamebuddy --format=custom \
  > "$DUMP"

# Off the box: a backup on the same disk is not a backup.
rclone copy "$DUMP" r2:gamebuddy-backups/

# Seven days, both sides. 168h rather than "7d" because rclone's --min-age takes a duration
# and hours are unambiguous.
find /home/gamebuddy/backups -name '*.dump' -mtime +7 -delete
rclone delete r2:gamebuddy-backups/ --min-age 168h
