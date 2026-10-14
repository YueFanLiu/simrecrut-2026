# Local setup

Local configuration lives in `.env` at the repository root. The tracked
`.env.example` describes the variable names and intended recipients. This page
owns setup instructions; deployment guides should reference it for variable
semantics and describe only their deployment-specific mapping.

## Create local configuration

Copy `.env.example` to `.env` if the file does not exist, then edit its values.
The initial scaffold's local file selects the installed Windows JDK 17 and leaves
credentials empty. Do not copy credentials from another project.

```powershell
Copy-Item .env.example .env
```

Set `JAVA_HOME` to the JDK 17 installation directory, such as `D:/Java/jdk-17`.
Select that JDK in the IDE or set it for the current shell before invoking the
Maven Wrapper. Maven and native Spring Boot do not load `.env` automatically.

```powershell
$env:JAVA_HOME = 'D:/Java/jdk-17'
$env:PATH = "$env:JAVA_HOME/bin;$env:PATH"
.\backend\java\mvnw.cmd -f backend/java/pom.xml --batch-mode verify
```

`DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` stay empty until cloud MySQL access is
provided. Compilation does not require the database. Backend startup requires a
valid database connection and the migrations for the implemented feature set.
The scaffold does not yet provide the complete application stack.

## Environment ownership

| Variables | Recipient |
| --- | --- |
| `JAVA_HOME` | Maven and the Java IDE. |
| `JAVA_PORT`, `SPRING_PROFILES_ACTIVE` | Java backend. |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Java backend only. |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | Java backend only. |
| `ML_BASE_URL` | Java backend only. |
| `ML_INTERNAL_TOKEN` | Java backend and internal inference service. |
| `DEEPSEEK_BASE_URL`, `DEEPSEEK_API_KEY`, `DEEPSEEK_MODEL` | Java extraction integration and offline extraction scripts. |
| `CASE_WORKSPACE_PATH` | Java backend's restricted temporary-file workspace. |
| `MODEL_ARTIFACTS_PATH` | Java package validation and read-only inference loading. |

The frontend receives public values only. The restored offline extraction
scripts read only the three extraction settings from their explicit local file.
Other offline jobs receive their own data and configuration; do not inject backend credentials.
Future Compose files must declare environment variables per service; reading a
root `.env` for interpolation does not inject those variables automatically.

## File format and paths

The offline extraction settings reader supports one literal `NAME=value` assignment per line,
blank lines, full-line `#` comments, and optional matching single or double quotes
around values. It does not execute shell expressions or expand variables. Use
the entire value after `=`; inline comments are not supported. A local `.env`
overrides matching extraction environment values within the script process.

Use the repository root as the Java application's working directory so relative
workspace and model paths resolve from that root. Configure this in the IDE or
with `spring-boot.run.workingDirectory` for Maven. Container paths will be
explicit mount targets in deployment configuration.

Keep `.env`, temporary CVs, actual datasets, run output, and model weights out of
Git. Directory markers preserve the required layout. Extraction source files
and their dependency declarations are restored. The local Python environment
has not been rebuilt, and datasets remain outside this checkout.

## Repository verification

From the repository root, run the language, layout, and documentation check with
Python 3.9 or newer:

```text
python scripts/maintenance/check_repository.py
```

See [repository checks](repository-checks.md) for its scope and limitations.

## References

- [Spring Boot external configuration](https://docs.spring.io/spring-boot/reference/features/external-config.html)
- [Docker Compose environment variables](https://docs.docker.com/compose/how-tos/environment-variables/set-environment-variables/)
