#!/usr/bin/env bash
# Run on YOUR LAPTOP weekly. Pull DB backup from VM.
# Usage: ./backup.sh ubuntu@yourname.duckdns.org
set -euo pipefail
HOST="$1"
ssh "$HOST" "sqlite3 ~/nido-data/db/nido.db \".backup '/tmp/nido.bak'\""
scp "$HOST:/tmp/nido.bak" "./nido-$(date +%F).bak"
echo "Saved nido-$(date +%F).bak"
