#!/bin/zsh

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
SCRIPT_PATH="$ROOT_DIR/create-kafka-topic.sh"

if [[ ! -f "$SCRIPT_PATH" ]]; then
  echo "missing script: $SCRIPT_PATH" >&2
  exit 1
fi

output="$(
  KAFKA_TOPICS_BIN="/tmp/mock-kafka-topics.sh" \
  MOCK_ONLY=1 \
  zsh "$SCRIPT_PATH" demo-topic --bootstrap-server localhost:29092 --partitions 6 \
    --replication-factor 3 --min-in-sync-replicas 2 --retention-ms 86400000
)"

[[ "$output" == *"demo-topic"* ]]
[[ "$output" == *"--bootstrap-server localhost:29092"* ]]
[[ "$output" == *"--partitions 6"* ]]
[[ "$output" == *"--replication-factor 3"* ]]
[[ "$output" == *"min.insync.replicas=2"* ]]
[[ "$output" == *"retention.ms=86400000"* ]]
