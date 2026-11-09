#!/usr/bin/env bash
# Regenera app/src/main/assets/adult_domains.txt a partir da lista
# "porn-only" do projeto StevenBlack/hosts (licença MIT).
set -euo pipefail
URL="https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn-only/hosts"
OUT="$(cd "$(dirname "$0")/.." && pwd)/app/src/main/assets/adult_domains.txt"
curl -fsSL "$URL" \
  | sed -e 's/#.*//' \
  | awk 'NF>=2 && $1 ~ /^[0-9.:]+$/ {print tolower($2)} NF==1 {print tolower($1)}' \
  | sed -e 's/\.$//' \
  | grep -v -E '^(localhost|local|broadcasthost|0\.0\.0\.0)$' \
  | grep '\.' \
  | sort -u > "$OUT"
echo "$(wc -l < "$OUT") domínios gravados em $OUT"
