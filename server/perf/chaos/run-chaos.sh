#!/usr/bin/env bash
set -euo pipefail

scenario="${1:-}"
duration="${CHAOS_DURATION_SECONDS:-15}"
artifact_dir="${ARTIFACT_DIR:-server/perf/artifacts/${RUN_ID:-manual}}"
mkdir -p "$artifact_dir"

case "$scenario" in
  postoffice-restart) start_var=POSTOFFICE_RESTART_CMD; heal_var=POSTOFFICE_HEAL_CMD ;;
  redis-short-outage) start_var=REDIS_OUTAGE_CMD; heal_var=REDIS_HEAL_CMD ;;
  kafka-broker-unavailable) start_var=KAFKA_OUTAGE_CMD; heal_var=KAFKA_HEAL_CMD ;;
  mongo-primary-stepdown) start_var=MONGO_STEPDOWN_CMD; heal_var=MONGO_HEAL_CMD ;;
  *)
    echo "usage: $0 {postoffice-restart|redis-short-outage|kafka-broker-unavailable|mongo-primary-stepdown}" >&2
    exit 2
    ;;
esac

start_cmd="${!start_var:-}"
heal_cmd="${!heal_var-}"
if [[ -z "$heal_cmd" ]]; then
  heal_cmd=true
fi
if [[ -z "$start_cmd" ]]; then
  echo "$start_var 未配置；拒绝猜测部署拓扑。请按 runbook 注入明确的故障命令。" >&2
  exit 2
fi
if [[ "${CONFIRM_CHAOS:-no}" != "yes" ]]; then
  echo "DRY RUN scenario=$scenario duration=${duration}s"
  echo "start: $start_cmd"
  echo "heal:  $heal_cmd"
  exit 0
fi

cleanup() { bash -lc "$heal_cmd" || true; }
trap cleanup EXIT INT TERM
date -Ins > "$artifact_dir/${scenario}.started"
bash -lc "$start_cmd"
sleep "$duration"
cleanup
trap - EXIT INT TERM
date -Ins > "$artifact_dir/${scenario}.healed"
echo "故障窗口结束；保持 k6 运行至恢复观察窗口结束，再执行 verify-summary.mjs。"
