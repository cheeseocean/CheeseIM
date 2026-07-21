#!/bin/zsh

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
CREATE_TOPIC_SCRIPT="$ROOT_DIR/create-kafka-topic.sh"
TOPIC_NAMES_FILE="$ROOT_DIR/../server/common-core/src/main/java/com/cheeseocean/im/common/core/constants/TopicNames.java"

bootstrap_server=""
partitions=""
replication_factor=""
min_in_sync_replicas=""
retention_ms=""
kafka_home=""
dry_run=0

usage() {
  cat <<'EOF'
Usage:
  ./distro/create-im-topics.sh [options]

Options:
  --bootstrap-server <host:port>
  --partitions <count>
  --replication-factor <count>
  --min-in-sync-replicas <count>
  --retention-ms <milliseconds>
  --kafka-home <path>
  --dry-run
  -h, --help

This script reads topic names from:
  server/common-core/src/main/java/com/cheeseocean/im/common/core/constants/TopicNames.java
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --bootstrap-server)
      bootstrap_server="${2:-}"
      shift 2
      ;;
    --partitions)
      partitions="${2:-}"
      shift 2
      ;;
    --replication-factor)
      replication_factor="${2:-}"
      shift 2
      ;;
    --min-in-sync-replicas)
      min_in_sync_replicas="${2:-}"
      shift 2
      ;;
    --retention-ms)
      retention_ms="${2:-}"
      shift 2
      ;;
    --kafka-home)
      kafka_home="${2:-}"
      shift 2
      ;;
    --dry-run)
      dry_run=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    --*)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 1
      ;;
    *)
      echo "Unexpected argument: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

if [[ ! -x "$CREATE_TOPIC_SCRIPT" ]]; then
  echo "Missing executable helper script: $CREATE_TOPIC_SCRIPT" >&2
  exit 1
fi

if [[ ! -f "$TOPIC_NAMES_FILE" ]]; then
  echo "Missing TopicNames file: $TOPIC_NAMES_FILE" >&2
  exit 1
fi

topic_names=()
topic_tmp_file="$(mktemp)"
trap 'rm -f "$topic_tmp_file"' EXIT

sed -En 's/.*public static final String [A-Z_][A-Z_0-9]*[[:space:]]*=[[:space:]]*"([^"]*)";.*/\1/p' \
  "$TOPIC_NAMES_FILE" > "$topic_tmp_file"

while IFS= read -r topic_name || [ -n "$topic_name" ]; do
  if [[ -n "$topic_name" ]]; then
    topic_names+=("$topic_name")
  fi
done < "$topic_tmp_file"

if [[ ${#topic_names[@]} -eq 0 ]]; then
  echo "No topic names found in: $TOPIC_NAMES_FILE" >&2
  exit 1
fi

extra_args=()
if [[ -n "$bootstrap_server" ]]; then
  extra_args+=(--bootstrap-server "$bootstrap_server")
fi
if [[ -n "$partitions" ]]; then
  extra_args+=(--partitions "$partitions")
fi
if [[ -n "$replication_factor" ]]; then
  extra_args+=(--replication-factor "$replication_factor")
fi
if [[ -n "$min_in_sync_replicas" ]]; then
  extra_args+=(--min-in-sync-replicas "$min_in_sync_replicas")
fi
if [[ -n "$retention_ms" ]]; then
  extra_args+=(--retention-ms "$retention_ms")
fi
if [[ -n "$kafka_home" ]]; then
  extra_args+=(--kafka-home "$kafka_home")
fi

echo "Discovered ${#topic_names[@]} IM base topics from TopicNames.java"

for topic_name in "${topic_names[@]}"; do
  for concrete_topic in "$topic_name" "$topic_name.DLT"; do
    echo "==> $concrete_topic"
    command_args=("$CREATE_TOPIC_SCRIPT" "$concrete_topic")
    if [[ ${#extra_args[@]} -gt 0 ]]; then
      command_args+=("${extra_args[@]}")
    fi
    if [[ "$dry_run" == "1" ]]; then
      printf '%q ' "${command_args[@]}"
      printf '\n'
      continue
    fi
    if [[ "${MOCK_ONLY:-0}" == "1" ]]; then
      MOCK_ONLY=1 "${command_args[@]}"
      continue
    fi
    "${command_args[@]}"
  done
done
