# Independent account and permission management

The backend uses application JWTs, Redis sessions, MyBatis and `sim_user`, without
RuoYi. Apply Flyway V7 after V1-V6. V7 adds account/role revisions and management
permissions for the existing ADMIN role. It creates no accounts or credentials.

## Endpoints

All responses use `{code, message, requestId, data}`, success code zero and
`Cache-Control: no-store`. Admin routes require ADMIN plus the indicated action.

| Method | Path | Permission suffix / access | Function |
| --- | --- | --- | --- |
| GET | `/api/v1/admin/users` | `user:list` | Paged safe summaries |
| GET | `/api/v1/admin/users/{userId}` | `user:list` | Account detail |
| POST | `/api/v1/admin/users` | `user:create` | Create an unverified account |
| PUT | `/api/v1/admin/users/{userId}` | `user:edit` | Change email and require verification |
| PUT | `/api/v1/admin/users/{userId}/status` | `user:status` | Enable or disable |
| PUT | `/api/v1/admin/users/{userId}/roles` | `user:roles` | Replace role set |
| GET | `/api/v1/admin/roles` | `role:list` | Existing fixed role catalogue |
| GET | `/api/v1/admin/roles/{roleCode}/permissions` | `role:list` | Read permissions and revision |
| PUT | `/api/v1/admin/roles/{roleCode}/permissions` | `role:edit` | Replace scoped permissions |
| GET | `/api/v1/admin/permissions` | `permission:list` | Server permission catalogue |
| GET | `/api/v1/auth/password-reset/options` | Public | SMTP configuration availability |
| POST | `/api/v1/auth/password-reset/code` | Public | Request a mailbox code |
| POST | `/api/v1/auth/password-reset` | Public | Set password with mailbox proof |

Permission strings have the `simrecrut:` prefix. Role codes are CANDIDATE, HR,
RESEARCHER and ADMIN. Account paths use public 26-character IDs, not database IDs.
No arbitrary role creation, permission creation, user deletion or RuoYi aliases
are provided. Administrative privilege alone does not grant Candidate content access.

## Requests and projections

| Operation | JSON fields |
| --- | --- |
| Create | `username`, `email`, `roles` |
| Edit | `email`, `revision` |
| Status | `status`, `revision` |
| Assign roles | `roles`, `revision` |
| Replace permissions | `permissions`, `revision` |
| Request code | `email` |
| Reset | `email`, `challengeId`, `emailCode`, `password`, `passwordConfirmation` |

Unknown body fields are rejected. Usernames are immutable and normalized to
lowercase. Roles must be nonempty, distinct and limited to the four fixed roles.
Status is ACTIVE or DISABLED. Revisions are positive; stale writes return 409.
Creation returns 201; other successes return 200. Creation is not replay-safe:
after an ambiguous error, search the unique username/email before trying again.
After a revision-changing PUT succeeds, retrying with the old revision returns
409; reload to inspect the saved outcome.

User list queries accept `page` (default 1, maximum 100000), `pageSize` (default 20,
maximum 100), `keyword` (maximum 100), optional `status` and optional `role`.
Keyword matches username/email by literal substring. Pagination data contains
`items`, `total`, `page`, `pageSize`.

User projections contain `userId`, `username`, `email`, `status`, `emailVerified`,
`passwordSetupRequired`, `roles`, `revision`, `createdAt`, `updatedAt`. Password
hashes, security versions and internal row IDs are never returned. Role reads
return `roleCode`, `revision`, `permissions`. The permission catalogue also
reports `allowedRoles` and `protectedAdminPermission`.

## Mailbox password setup and recovery

Admin creation generates an undisclosed credential and leaves the mailbox
unverified. It does not send mail automatically. The recipient opens
`/forgot-password`, requests a code and sets their own password. Until then the
account cannot sign in. Email changes require the same proof flow and invalidate
existing sessions. Disabled accounts must be enabled before password recovery.

Options return `emailEnabled`, indicating SMTP credential presence, not delivery.
Code requests return `challengeId`, `expiresInSeconds: 600`,
`resendAfterSeconds: 60`. Unknown/disabled emails receive the same public shape
and generic message without a challenge being stored or mail being sent.
Operational failures return safe errors; no code is returned in HTTP responses.

SecureRandom generates six-digit codes. HMAC-SHA256 binds purpose, challenge and
normalized email using the existing server registration-code secret. Redis stores
only a digest and account/security-version snapshot. Registration and password
proofs are separate. Codes expire after ten minutes, are consumed once and are
invalidated after five wrong attempts. Sending is limited to one per email per
minute, five per email per hour and ten per IP per hour. Submissions, including
unknown challenges, are limited to sixty per IP per ten minutes.

Passwords contain 15-128 Unicode code points and must match confirmation.
Consumption and password hashing precede the short MySQL transaction. A guarded
update checks email, ACTIVE status and frozen security version, preventing stale
proofs from undoing later password, role, email or status changes. Success verifies
the mailbox, increments revision/security version and records an audit event.
Every old JWT fails the existing database security-version check; Redis session
records expire at their normal TTL. No token-key scanning is needed.

If storage fails after proof consumption, request a fresh code. Server-only SMTP
configuration and the existing desktop SMTP-file loader remain in use.

## Administrative consistency

All administrative writes first lock fixed role rows in ascending ID order,
then recheck the actor's current role, action permission and security version.
Account writes lock the target and verify revision. This serializes last-admin
checks across processes. The last ACTIVE, verified Admin cannot be disabled,
demoted or made unverified through email editing. Self-disable and removal of
one's own ADMIN role are rejected.

Permissions must exist in the server catalogue and belong to the role's scope.
ADMIN core permissions are protected. Resume permissions remain Candidate-only;
job management remains HR-only. Case grants are separate. Changed grants
invalidate affected member sessions; no-op updates preserve revisions/sessions.
Every mutation records a transactional non-content audit event containing actor,
action, resource type and hashed reference, without codes, passwords or emails.

## Verification boundaries

Tests cover role/action access, strict HTTP bodies, safe projections, revisions,
self/last-admin protection, permission scope, mailbox binding, invalid proofs,
rate limits and guarded security-version checks. Mapper XML is parsed without a
shared database. Swagger/OpenAPI describes the actual routes and typed responses.
The existing recovery page uses this contract without a CAPTCHA.

V7 execution, real MySQL concurrency and real SMTP delivery require validation
in the target environment. The legacy Admin frontend forms remain a separate
integration task: their RuoYi/mock single-role field shapes are not this API's
contract. Use public IDs, role arrays, revisions and the server permission catalogue.
