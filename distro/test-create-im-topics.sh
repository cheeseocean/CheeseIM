#!/bin/zsh

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
SCRIPT_PATH="$ROOT_DIR/create-im-topics.sh"

if [[ ! -f "$SCRIPT_PATH" ]]; then
  echo "missing script: $SCRIPT_PATH" >&2
  exit 1
fi

output="$(
  MOCK_ONLY=1 \
  zsh "$SCRIPT_PATH" --bootstrap-server localhost:29092 --partitions 4 \
    --replication-factor 1 --min-in-sync-replicas 1 --retention-ms 86400000
)"

[[ "$output" == *"ingress"* ]]
[[ "$output" == *"history"* ]]
[[ "$output" == *"delivery"* ]]
[[ "$output" == *"delivery-outcome"* ]]
[[ "$output" == *"group-fanout"* ]]
[[ "$output" == *"offlinepush"* ]]
[[ "$output" == *"ingress.DLT"* ]]
[[ "$output" == *"offlinepush.DLT"* ]]
[[ "$output" == *"--bootstrap-server localhost:29092"* ]]
[[ "$output" == *"--partitions 4"* ]]
[[ "$output" == *"min.insync.replicas=1"* ]]
[[ "$output" == *"retention.ms=86400000"* ]]
