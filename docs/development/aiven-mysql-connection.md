# Aiven MySQL connection

Verified on 2026-10-10 from the local Windows workspace using JDK 17 and MySQL Connector/J 9.4.0.

## Configuration

- The root `.env` contains the JDBC URL, database username and database password.
- `.local/aiven/ca.pem` is the project CA certificate retrieved from the authenticated Aiven service overview.
- `.local/aiven/truststore.p12` contains that CA certificate for Java TLS verification.
- Both `.env` and `.local/` are ignored by Git. Keep them on this computer; they are not included in a fresh clone.
- The JDBC URL requires `sslMode=VERIFY_IDENTITY`, with the explicit PKCS12 truststore and no fallback to the system truststore.
- The truststore contains a public CA certificate only. Its store password is not the database password.
- `scripts/dev/java.ps1` already loads root `.env` values into the Java process. No frontend environment variable contains database credentials.

## Verification result

| Check | Result |
| --- | --- |
| Aiven service state | Running on the existing Free plan |
| JDBC connection | Passed |
| Database | `defaultdb` |
| MySQL version | `8.4.11` |
| TLS cipher | `TLS_AES_256_GCM_SHA384` |
| CA and hostname verification | Enabled |
| Database changes | None |

The standalone probe uses a read-only connection and executes only `SELECT 1, DATABASE(), VERSION()` and `SHOW SESSION STATUS LIKE 'Ssl_cipher'`. It does not load Spring Boot or Flyway.

To repeat the probe from the repository root in PowerShell:

```powershell
& 'D:/Java/jdk-17/bin/java.exe' -cp '.local/aiven;.local/aiven/mysql-connector-j-9.4.0.jar' CheckDatabase
```

The local probe source and class are stored in `.local/aiven/`. The connector was downloaded from Maven Central and its checksum was checked against the published checksum.

## Scope and next steps

This verifies the configured Java database connection. It does not implement application APIs, real authentication, table mappings or frontend integration. The existing frontend continues using mock data.

The Spring Boot configuration currently enables Flyway migrations automatically. Review migrations and the intended database before starting the full backend. Redis and the internal inference service also have separate configuration requirements.

If the workspace moves, update the absolute truststore URL in `.env`. If Aiven rotates the project CA certificate, replace the local certificate and rebuild the truststore before testing again.

References: [Aiven TLS certificates](https://aiven.io/docs/platform/concepts/tls-ssl-certificates), [MySQL Connector/J server authentication](https://dev.mysql.com/doc/connector-j/en/connector-j-server-authentication.html).
