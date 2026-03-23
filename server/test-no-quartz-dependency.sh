#!/bin/zsh

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"

output="$(cd "$ROOT_DIR" && ./gradlew :push:dependencies --configuration runtimeClasspath)"

if echo "$output" | rg -q "spring-boot-starter-quartz|org\.quartz-scheduler:quartz" -i; then
  echo "Quartz dependency is still present in :push runtimeClasspath" >&2
  exit 1
fi
