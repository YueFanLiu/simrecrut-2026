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
The system Java command may select another JDK; use the project helper below.
It changes the environment for its process only.

```powershell
.\scripts\dev\java.ps1 -Action verify
```

`DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` stay empty until cloud MySQL access is
provided. Compilation does not require the database. Backend startup requires a
valid database connection and the migrations for the implemented feature set.
The scaffold does not yet provide the complete application stack.

## Environment ownership

| Variables | Recipient |
| --- | --- |
| `JAVA_HOME` | Native Java development helper. |
| `JAVA_PORT`, `SPRING_PROFILES_ACTIVE` | Java backend. |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Java backend only. |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | Java backend only. |
| `ML_BASE_URL` | Java backend only. |
| `ML_INTERNAL_TOKEN` | Java backend and internal inference service. |
| `DEEPSEEK_BASE_URL`, `DEEPSEEK_API_KEY`, `DEEPSEEK_MODEL` | Java extraction integration and the local offline extraction command. |
| `CASE_WORKSPACE_PATH` | Java backend's restricted temporary-file workspace. |
| `MODEL_ARTIFACTS_PATH` | Java package validation and read-only inference loading. |

The frontend receives public values only. The offline extraction command reads
only the three extraction settings from the explicitly selected local file.
It does not inject database, Redis or internal inference credentials into its
environment. Other offline jobs receive their own approved data and configuration.
Future Compose files must declare environment variables per service; reading a
root `.env` for interpolation does not inject those variables automatically.

## File format and paths

The development helper supports one literal `NAME=value` assignment per line,
blank lines, full-line `#` comments, and optional matching single or double quotes
around values. It does not execute shell expressions or expand variables. Use
the entire value after `=`; inline comments are not supported. A local `.env`
overrides matching environment values within the helper process.

The helper runs Maven from the repository root. Relative workspace and model
paths therefore resolve from that root. Container paths will be explicit mount
targets in deployment configuration.

Keep `.env`, temporary CVs, actual datasets, run output, and model weights out of
Git. Directory markers and small dataset notes preserve guidance without
storing those files. Offline source preparation uses Python 3.12 or newer;
its package setup and commands are in [Dataset preparation](../research/dataset-preparation.md).

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
