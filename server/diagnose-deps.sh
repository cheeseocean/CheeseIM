#!/bin/bash

echo "=== Gradle依赖诊断脚本 ==="

echo "1. 检查Gradle版本..."
./gradlew --version

echo "2. 检查网络连接..."
curl -I https://maven.aliyun.com/repository/central/ || echo "阿里云仓库连接失败"
curl -I https://repo1.maven.org/maven2/ || echo "中央仓库连接失败"

echo "3. 清理缓存..."
./gradlew clean --refresh-dependencies

echo "4. 检查依赖树..."
./gradlew :common-core:dependencies --configuration compileClasspath
./gradlew :common-api:dependencies --configuration compileClasspath

echo "5. 尝试构建..."
./gradlew \
  :common-core:compileJava \
  :common-api:compileJava \
  :authcenter:compileJava \
  :postoffice:compileJava \
  :postbox:compileJava \
  :postman:compileJava \
  :push:compileJava \
  --info

echo "=== 诊断完成 ==="
