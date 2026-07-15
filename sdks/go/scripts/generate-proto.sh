#!/usr/bin/env sh

# 从服务端唯一协议源生成 Go 代码。不要在 SDK 目录维护 Proto 副本。
set -eu

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/../../.." && pwd)
proto_dir="$repo_root/server/common-api/src/main/proto"
output_dir="$repo_root/sdks/go/proto"

command -v protoc >/dev/null 2>&1 || {
  echo "protoc is required; install a protoc 25.x-compatible version." >&2
  exit 1
}
command -v protoc-gen-go >/dev/null 2>&1 || {
  echo "protoc-gen-go is required; run: go install google.golang.org/protobuf/cmd/protoc-gen-go@v1.36.6" >&2
  exit 1
}

protoc \
  --proto_path="$proto_dir" \
  --go_out=paths=source_relative,Mmessage_protocol.proto=github.com/cheeseim/cheeseim-go-sdk/proto:"$output_dir" \
  "$proto_dir/message_protocol.proto"
