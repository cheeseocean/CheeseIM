#!/bin/zsh

set -euo pipefail

DEFAULT_KAFKA_HOME="/Users/xxxcrel/Develop/middleware/kafka"
DEFAULT_BOOTSTRAP_SERVER="localhost:9092"
DEFAULT_PARTITIONS="12"
DEFAULT_REPLICATION_FACTOR="3"
DEFAULT_MIN_IN_SYNC_REPLICAS="2"
DEFAULT_RETENTION_MS="604800000"

topic_name=""
bootstrap_server="${BOOTSTRAP_SERVER:-$DEFAULT_BOOTSTRAP_SERVER}"
partitions="${PARTITIONS:-$DEFAULT_PARTITIONS}"
replication_factor="${REPLICATION_FACTOR:-$DEFAULT_REPLICATION_FACTOR}"
min_in_sync_replicas="${MIN_IN_SYNC_REPLICAS:-$DEFAULT_MIN_IN_SYNC_REPLICAS}"
retention_ms="${RETENTION_MS:-$DEFAULT_RETENTION_MS}"
kafka_home="${KAFKA_HOME:-$DEFAULT_KAFKA_HOME}"
kafka_topics_bin="${KAFKA_TOPICS_BIN:-}"

resolve_kafka_topics_bin() {
  local base_dir="$1"
  local candidate=""

  if [[ -x "$base_dir/bin/kafka-topics.sh" ]]; then
    echo "$base_dir/bin/kafka-topics.sh"
    return 0
  fi
  if [[ -x "$base_dir/bin/kafka-topics" ]]; then
    echo "$base_dir/bin/kafka-topics"
    return 0
  fi

  candidate="$(find "$base_dir" -maxdepth 3 \( -name 'kafka-topics.sh' -o -name 'kafka-topics' \) | head -n 1)"
  if [[ -n "$candidate" ]]; then
    echo "$candidate"
    return 0
  fi

  return 1
}

usage() {
  cat <<'EOF'
Usage:
  ./distro/create-kafka-topic.sh <topic-name> [options]

Options:
  --bootstrap-server <host:port>      Default: localhost:9092
  --partitions <count>                Default: 3
  --replication-factor <count>        Default: 3
  --min-in-sync-replicas <count>     Default: 2
  --retention-ms <milliseconds>       Default: 604800000 (7 days)
  --kafka-home <path>                 Default: /Users/xxxcrel/Develop/middleware/kafka
  -h, --help                          Show this help

Environment overrides:
  BOOTSTRAP_SERVER
  PARTITIONS
  REPLICATION_FACTOR
  MIN_IN_SYNC_REPLICAS
  RETENTION_MS
  KAFKA_HOME
  KAFKA_TOPICS_BIN

Examples:
  ./distro/create-kafka-topic.sh im.ingress
  ./distro/create-kafka-topic.sh im.delivery --partitions 6 --bootstrap-server localhost:29092
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
      if [[ -n "$topic_name" ]]; then
        echo "Only one topic name is supported per invocation." >&2
        usage >&2
        exit 1
      fi
      topic_name="$1"
      shift
      ;;
  esac
done

if [[ -z "$topic_name" ]]; then
  echo "Missing topic name." >&2
  usage >&2
  exit 1
fi

for numeric_value in "$partitions" "$replication_factor" "$min_in_sync_replicas" "$retention_ms"; do
  if ! [[ "$numeric_value" =~ ^[0-9]+$ ]] || (( numeric_value <= 0 )); then
    echo "Partitions, replication, minISR and retention must be positive integers." >&2
    exit 1
  fi
done
if (( min_in_sync_replicas > replication_factor )); then
  echo "min-in-sync-replicas cannot exceed replication-factor." >&2
  exit 1
fi

if [[ -z "$kafka_topics_bin" ]]; then
  if [[ "${MOCK_ONLY:-0}" == "1" ]]; then
    kafka_topics_bin="$kafka_home/bin/kafka-topics.sh"
  else
    kafka_topics_bin="$(resolve_kafka_topics_bin "$kafka_home" || true)"
    if [[ -z "$kafka_topics_bin" ]]; then
      echo "Cannot find kafka-topics script under: $kafka_home" >&2
      exit 1
    fi
  fi
fi

command_args=(
  "$kafka_topics_bin"
  --bootstrap-server "$bootstrap_server"
  --create
  --if-not-exists
  --topic "$topic_name"
  --partitions "$partitions"
  --replication-factor "$replication_factor"
  --config "min.insync.replicas=$min_in_sync_replicas"
  --config "retention.ms=$retention_ms"
)

if [[ "${MOCK_ONLY:-0}" == "1" ]]; then
  printf '%q ' "${command_args[@]}"
  printf '\n'
  exit 0
fi

echo "Creating topic '$topic_name' via $kafka_topics_bin"
"${command_args[@]}"
