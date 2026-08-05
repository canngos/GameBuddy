#!/usr/bin/env bash
#
# Publishes the generated art to the public media bucket.
#
#   tools/upload-assets.sh badges          # one prefix
#   tools/upload-assets.sh                 # all of them
#
# The three generators (default-avatars/, cosmetics/, badges/) write files; this puts
# them where the app reads them from. Nothing in the backend uploads these — they are
# build-time assets, not user content, and the object keys are seeded into the database
# by db/seed-local.sql. Which means: regenerate, upload, and the keys already match.
#
# Reads credentials from .env, so there is one place they live. The bucket is public;
# do not point this at gamebuddy-uploads, which holds unreviewed user images.
#
# Written after doing it by hand once. Doing it by hand is fine right up until the day
# a file is regenerated and only twelve of thirteen get re-uploaded.

set -euo pipefail

cd "$(dirname "$0")/.."

# shellcheck disable=SC2046
export $(grep -E '^R2_(ACCESS_KEY_ID|SECRET_ACCESS_KEY|ENDPOINT|MEDIA_BUCKET)=' .env | xargs)

export AWS_ACCESS_KEY_ID="$R2_ACCESS_KEY_ID"
export AWS_SECRET_ACCESS_KEY="$R2_SECRET_ACCESS_KEY"
# R2 has no regions, but the SDK refuses to sign without one.
export AWS_DEFAULT_REGION=auto

# local directory -> object key prefix
declare -A SOURCES=(
  [badges]="badges/icons"
  [frames]="cosmetics/frames"
  [banners]="cosmetics/banners"
  [default-avatars]="default-avatars"
)

targets=("$@")
if [ ${#targets[@]} -eq 0 ]; then
  targets=(badges frames banners default-avatars)
fi

for prefix in "${targets[@]}"; do
  src="${SOURCES[$prefix]:-}"
  if [ -z "$src" ]; then
    echo "unknown prefix: $prefix (known: ${!SOURCES[*]})" >&2
    exit 2
  fi

  echo "==> $src -> s3://$R2_MEDIA_BUCKET/$prefix/"
  # --exclude '*' then include only the art: the generators also leave contact sheets
  # and a generate.py next to their output, and neither belongs on a CDN.
  aws s3 sync "$src" "s3://$R2_MEDIA_BUCKET/$prefix/" \
    --endpoint-url "$R2_ENDPOINT" \
    --exclude '*' --include '*.png' --include '*.jpg' --include '*.webp' \
    --size-only
done
