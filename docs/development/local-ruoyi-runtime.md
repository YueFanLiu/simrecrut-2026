# Local RuoYi runtime

Run `scripts/dev/start-local.ps1` in PowerShell from the repository root.
Open http://127.0.0.1:5173/login and sign in as `admin`. The generated password
is stored only in the ignored `runtime/local-logs/ruoyi-login.txt`. The root
`.env` holds local database and token configuration. Do not commit those files.

The active runtime now connects to Aiven; see [Aiven integration](aiven-resume-integration.md).
The optional local MySQL instance listens on localhost:3307 and uses
`simrecrut_ruoyi_local`. RuoYi system tables, Quartz tables, Zheng's 36 business
tables and SimRecrut menu permissions have been initialized there. The sample
`ry` user is disabled and the initial administrator password was replaced.
The existing MySQL instance and Aiven are separate.

Redis listens on localhost:6379. This local runtime uses the portable
[redis-windows build](https://github.com/redis-windows/redis-windows), because
Docker Desktop could not start. Local database and Redis runtime files reside
under the current user's LocalAppData. MySQL stores durable data; Redis stores
expiring sessions and challenges. The active Aiven identity and publication configuration stays in the ignored root .env.

The ignored `frontend/.env.development.local` selects real authentication.
Use `start-local.ps1 -Demo` to restore the prototype demonstration. Research
and assessment pages without business APIs remain demo-only.

Verification passed through the frontend API proxy: captcha, administrator
login, user information, menus, resume listing and logout. The old token was
rejected with 401 after logout. Local automated verification reads the one-time
test challenge from Redis; browser users solve the displayed challenge normally.

The startup script does not initialize or drop any tables. Inspect ignored logs
under `runtime/local-logs` if a service does not start. The frontend can become
ready before the Java service finishes starting.
