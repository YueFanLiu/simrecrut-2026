# Build context: backend/java (also use this as the Render Root Directory).
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /build
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
# Accept checkouts that use Windows line endings for the shell wrapper.
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
COPY src/ src/
RUN ./mvnw --batch-mode --no-transfer-progress package

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# PDFBox renders pages in Java; Tesseract handles scanned English/French PDFs.
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates \
        fontconfig \
        fonts-dejavu-core \
        tesseract-ocr \
        tesseract-ocr-eng \
        tesseract-ocr-fra \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 simrecrut \
    && useradd --system --uid 10001 --gid simrecrut \
        --home-dir /app --shell /usr/sbin/nologin simrecrut \
    && mkdir -p /app/runtime/cases /app/runtime/resumes /app/model_artifacts/rules \
    && chown -R simrecrut:simrecrut /app/runtime \
    && chmod -R 700 /app/runtime

COPY --from=build --chown=simrecrut:simrecrut /build/target/app.jar /app/app.jar

# Inject database, Redis, SMTP and application secrets at runtime in Render.
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_PORT=10000 \
    JAVA_TOOL_OPTIONS="-Djava.awt.headless=true -Dfile.encoding=UTF-8" \
    CASE_WORKSPACE_PATH=/app/runtime/cases \
    SIMRECRUT_RESUMES_DIRECTORY=/app/runtime/resumes \
    MODEL_ARTIFACTS_PATH=/app/model_artifacts \
    JOB_RULE_ROOT=/app/model_artifacts/rules \
    TESSERACT_EXECUTABLE=/usr/bin/tesseract \
    TESSERACT_LANGUAGES=eng+fra

USER simrecrut:simrecrut
EXPOSE 10000

# Render supplies PORT. JAVA_PORT remains available for local container runs.
# exec forwards shutdown signals to Java so Spring can stop gracefully.
ENTRYPOINT ["sh", "-c", "exec java -jar /app/app.jar --server.address=0.0.0.0 --server.port=\"${PORT:-${JAVA_PORT:-10000}}\""]
