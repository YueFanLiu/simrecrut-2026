FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /build
# Git retrieves the pinned official RuoYi source during dependency installation.
RUN apt-get update && apt-get install -y --no-install-recommends git && rm -rf /var/lib/apt/lists/*
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw
RUN MAVEN_CMD=/build/mvnw bash .mvn/bootstrap-ruoyi.sh
COPY src/ src/
RUN ./mvnw --batch-mode --no-transfer-progress package

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN groupadd --system simrecrut && useradd --system --gid simrecrut simrecrut
COPY --from=build --chown=simrecrut:simrecrut /build/target/app.jar /app/app.jar
USER simrecrut
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
