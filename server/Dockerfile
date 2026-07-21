# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:17-jdk-jammy AS builder

ARG MODULE
WORKDIR /workspace

COPY . .

RUN case "${MODULE}" in \
        api-server|authcenter|business|postoffice|postbox|postmaster|postman) ;; \
        *) echo "Unsupported production module: ${MODULE}" >&2; exit 64 ;; \
    esac \
    && chmod +x gradlew \
    && ./gradlew ":${MODULE}:bootJar" --no-daemon \
    && find "${MODULE}/build/libs" -maxdepth 1 -type f \
        -name '*.jar' ! -name '*-plain.jar' \
        -exec cp '{}' /workspace/app.jar ';' \
    && test -s /workspace/app.jar \
    && java -Djarmode=layertools -jar /workspace/app.jar \
        extract --destination /workspace/layers

FROM eclipse-temurin:17-jre-jammy

ARG MODULE
ARG VERSION=dev

LABEL org.opencontainers.image.title="CheeseIM ${MODULE}" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.source="https://github.com/cheeseocean/CheeseIM"

RUN groupadd --system --gid 10001 cheeseim \
    && useradd --system --uid 10001 --gid cheeseim --home-dir /app cheeseim \
    && mkdir -p /app/logs /app/data \
    && chown -R cheeseim:cheeseim /app

WORKDIR /app
COPY --from=builder --chown=cheeseim:cheeseim /workspace/layers/dependencies/ ./
COPY --from=builder --chown=cheeseim:cheeseim /workspace/layers/spring-boot-loader/ ./
COPY --from=builder --chown=cheeseim:cheeseim /workspace/layers/snapshot-dependencies/ ./
COPY --from=builder --chown=cheeseim:cheeseim /workspace/layers/application/ ./

USER 10001:10001

ENV SPRING_PROFILES_ACTIVE=cluster \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=25.0 -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
