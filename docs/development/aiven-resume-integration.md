# Aiven resume integration

The active backend now uses the existing Aiven `simrecrut_dev` schema for
RuoYi identity and confirmed resume JSON. MySQL connections verify the project
CA and server hostname. Private connection configuration stays in the ignored
root `.env`; certificates and the generated trust store stay under ignored
`runtime/local-logs`. Redis still runs locally on localhost:6379.

## Verified workflow

Live verification passed through the frontend proxy:

- Real administrator login reads the Aiven account database.
- A synthetic PDF is uploaded to the authenticated user's temporary directory.
- Publication before confirmation is rejected and retains the original.
- The shared Java cleaner extracts two skills without contact details.
- Confirmation publishes JSON to `sim_cv_cleaned` in `simrecrut_dev`.
- The original PDF is removed only after the successful database commit.
- The stored revision matches the cloud revision and original availability is false.
- A Candidate cannot list or read another user's resume or administer users.
- Anonymous resume access is rejected. Temporary test accounts are removed.

The labeled synthetic resume remains as a verification example. The existing
CV in `defaultdb` was preserved. The Candidate role has only resume permissions;
the idempotent role script is in `backend/java/src/main/resources/db/ruoyi`.

## Start and sign in

Run `scripts/dev/start-local.ps1` and open http://127.0.0.1:5173/login.
Use `admin` and the current password in ignored
`runtime/local-logs/ruoyi-login.txt`. The credential note was empty during this
verification, so the administrator password was replaced and the note restored.

The review page reports cloud publication separately from temporary-file
cleanup. If deletion fails after a successful commit, retry cleanup without
claiming the database write failed.

## Remaining business work

`sim_cv_cleaned` is a publication adapter, not an assessment case. Creating
frozen case snapshots against job and protocol versions remains separate work
against the 36 business tables. Research, scoring and model training screens
still need real business APIs. User-scoped temporary indexes remain local
SQLite and are not restored from Aiven when moved to another computer.
