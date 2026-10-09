# Java backend setup

The backend requires JDK 17 for compilation and execution. Maven Enforcer rejects
other major JDK versions, and the compiler targets Java 17 bytecode. The Docker
build and runtime also use Java 17. The application entry point rejects a
different runtime major version, including direct `java -jar` invocations.

## Pinned toolchain

| Component | Version |
| --- | --- |
| Java | 17 |
| Spring Boot | 3.5.16 |
| MyBatis Spring Boot Starter | 3.0.5 |
| Maven Wrapper plugin | 3.3.4 |
| Maven distribution | 3.9.16 |
| Maven Enforcer plugin | 3.6.3 |

Spring Boot manages the compatible versions of Spring Security, Redis, Flyway,
MySQL Connector/J, and the other Spring dependencies.

## Build

Use the repository development script described in [local setup](local-setup.md).
It loads the root `.env`, validates JDK 17, and invokes the committed wrapper.
Spring Boot does not load a dotenv file automatically.

To build directly from the repository root in PowerShell, select a JDK for the
current process only:

```powershell
$env:JAVA_HOME = 'D:/Java/jdk-17'
$env:PATH = "$env:JAVA_HOME/bin;$env:PATH"
Push-Location backend/java
./mvnw.cmd --version
./mvnw.cmd --batch-mode --no-transfer-progress verify
Pop-Location
```

On Linux or macOS, set `JAVA_HOME` to the local JDK 17 installation and run:

```sh
cd backend/java
./mvnw --batch-mode --no-transfer-progress verify
```

The executable artifact is `backend/java/target/app.jar`. The first wrapper run
needs network access to download Maven and the project dependencies. Generated
build artifacts and machine-specific JDK paths do not belong in Git.
The wrapper verifies the pinned Maven distribution with a SHA-256 checksum,
derived after checking Maven Central's published SHA-512 checksum.

## Runtime prerequisites

Configure a reachable MySQL database and Redis service before starting the
backend. Supply their settings through the root `.env` locally, or through
explicit service environment variables when deployed. Do not put credentials in
the application YAML files. See [local setup](local-setup.md) for the shared
variable definitions.

Flyway is enabled, with migrations kept in
`backend/java/src/main/resources/db/migration/`. No business schema migrations
have been implemented in this scaffold. A successful build does not establish
database connectivity or prove that a complete business application is ready.

The scaffold supplies the application entry point, dependency configuration, and
restricted health endpoints. It implements no business APIs, authentication
flow, model invocation, or online training. Business routes are denied until the
identity and resource authorization modules are implemented. Spring Boot's
generated development user is disabled.

Only Actuator health is exposed, without component details or configuration
values. Liveness reports application lifecycle state; readiness also checks
MySQL and Redis. The planned ML readiness check must be added when the internal
gateway and model loading are implemented. Keep health endpoints on the service
network and follow the deployment proxy configuration.

## Container build

The Java Dockerfile expects `backend/java/` as its build context. From the
repository root, the build command is:

```sh
docker build -f backend/java/Dockerfile -t simrecrut-backend:local backend/java
```

Its build stage uses JDK 17 and its runtime stage uses Java 17. Runtime credentials
come from service-specific environment injection; they are not copied into the
image. Docker execution and database connectivity have not been verified by the
initial scaffold build.

## Official references

- [Spring Boot 3.5 system requirements](https://docs.spring.io/spring-boot/3.5/system-requirements.html)
- [MyBatis starter compatibility](https://mybatis.org/spring-boot-starter/mybatis-spring-boot-autoconfigure/)
- [Maven Wrapper](https://maven.apache.org/tools/wrapper/)
- [Maven Enforcer Java version rule](https://maven.apache.org/enforcer/enforcer-rules/requireJavaVersion.html)
- [Spring Boot external configuration](https://docs.spring.io/spring-boot/3.5/reference/features/external-config.html)
- [Spring Boot Actuator endpoints](https://docs.spring.io/spring-boot/3.5/reference/actuator/endpoints.html)
