# Build stage.
#
# Builds inside the image rather than copying a jar from the host, so `docker compose up`
# works from a clean checkout with nothing but Docker installed — which is the whole point
# of the local environment existing.
FROM eclipse-temurin:25-jdk AS build

WORKDIR /src

# Wrapper and build definitions first. These change rarely, so the dependency download
# below stays cached across ordinary source edits.
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
COPY common/build.gradle common/
COPY GameBuddy-backend/build.gradle GameBuddy-backend/

# Strip CR before anything tries to run it. Checked out on Windows, gradlew has CRLF line
# endings, and Linux then reads the shebang as "/bin/sh\r" — a program that does not exist.
# The error is "./gradlew: not found" for a file that is plainly there, which is a memorable
# twenty minutes the first time.
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

RUN ./gradlew --no-daemon dependencies --quiet || true

COPY common common
COPY GameBuddy-backend GameBuddy-backend

RUN ./gradlew --no-daemon :backend:bootJar -x test

# Runtime stage.
#
# JRE rather than JDK, and no build tooling: a smaller image with less in it to be
# vulnerable.
FROM eclipse-temurin:25-jre

# curl is not in the JRE image and the healthcheck needs it. Without a healthcheck the
# backend reports "started" the instant the container process exists, and anything waiting
# on it proceeds while Spring is still wiring beans.
RUN apt-get update \
    && apt-get install --no-install-recommends -y curl \
    && rm -rf /var/lib/apt/lists/*

# Runs as a non-root user. If the process is ever compromised, it should not also be root
# inside its container.
RUN groupadd --system gamebuddy && useradd --system --gid gamebuddy --create-home gamebuddy

WORKDIR /app
COPY --from=build /src/GameBuddy-backend/build/libs/backend-*.jar app.jar
RUN chown gamebuddy:gamebuddy app.jar

USER gamebuddy
EXPOSE 8080

# Container-aware heap sizing. Without it the JVM sizes against the host's memory and gets
# OOM-killed by the container limit under load.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC"

# Exec form via sh so JAVA_OPTS expands, but with exec so the JVM is PID 1 and receives
# SIGTERM directly — otherwise shutdown waits for the ten-second kill timeout on every
# restart.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
