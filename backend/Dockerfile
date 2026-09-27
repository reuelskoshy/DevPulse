# DevPulse API image: build the Spring Boot jar with Maven, then run it on a JRE as a non-root user.
# The build context is this directory (backend/):
#   docker build -t devpulse-api ./backend

# ---- Build stage ---------------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Resolve dependencies in their own layer so source-only changes reuse the cached download.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# maven.test.skip also skips compiling src/test: tests run in local/CI builds, not in the image.
RUN mvn -B -Dmaven.test.skip=true package

# spring-boot-maven-plugin repackages target/<artifactId>-<version>.jar into the executable jar and
# keeps the plain one as *.jar.original, so target/*.jar matches only the executable jar. Fail loudly
# if that ever stops being true (for example, an extra classifier jar appears).
RUN set -- target/*.jar; \
    if [ "$#" -ne 1 ] || [ ! -f "$1" ]; then \
      echo "Expected exactly one executable jar in target/, found: $*" >&2; \
      exit 1; \
    fi; \
    cp "$1" /workspace/app.jar

# ---- Runtime stage -------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd --system devpulse \
    && useradd --system --gid devpulse --home-dir /app --no-create-home --shell /usr/sbin/nologin devpulse

# Owned by root and read-only for the app user.
COPY --from=build /workspace/app.jar /app/app.jar

USER devpulse:devpulse

# Defaults sized for a 512 MB instance (e.g. Render's free plan): heap capped at 70% of the container
# limit, leaving room for metaspace/code cache; serial GC and C1-only JIT keep CPU and memory low on a
# fractional CPU. Override JAVA_OPTS at runtime for bigger instances.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:+ExitOnOutOfMemoryError"

# The app listens on $PORT when set (hosting platforms), otherwise $BACKEND_PORT, otherwise 8080.
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar \"$@\"", "--"]
