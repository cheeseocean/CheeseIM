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
  zsh "$SCRIPT_PATH" --bootstrap-server localhost:29092 --partitions 4 --replication-factor 1
)"

[[ "$output" == *"ingress"* ]]
[[ "$output" == *"history"* ]]
[[ "$output" == *"delivery"* ]]
[[ "$output" == *"receipt"* ]]
[[ "$output" == *"offlinepush"* ]]
[[ "$output" == *"retry"* ]]
[[ "$output" == *"dlq"* ]]
[[ "$output" == *"--bootstrap-server localhost:29092"* ]]
[[ "$output" == *"--partitions 4"* ]]
