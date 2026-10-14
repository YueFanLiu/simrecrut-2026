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
| `DEEPSEEK_BASE_URL`, `DEEPSEEK_API_KEY` | Java extraction integration only. |
| `CASE_WORKSPACE_PATH` | Java backend's restricted temporary-file workspace. |
| `MODEL_ARTIFACTS_PATH` | Java package validation and read-only inference loading. |

The frontend receives public values only. Offline jobs receive their own approved
data and configuration, rather than an unrestricted copy of backend credentials.
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
Git. The directory markers preserve the scaffold without storing those files.

## Frontend demo

Use Node.js 22.12 or newer and npm. From the repository root:

```powershell
cd frontend
npm ci
npm run dev
```

Open `http://127.0.0.1:5173/login`. No environment file or backend service is
required for the frontend demo. Sign in with username `demo` or email
`demo@simrecrut.test`, and password `SimRecrut123!`.

The Login page provides English validation, password visibility, loading and
failure feedback. Remember Me stores only the identifier in browser local
storage. The Pinia demo session is held in memory; refreshing the temporary
dashboard returns to Login. Sign out clears the in-memory session. Captcha and
password recovery are not implemented.

Open `/register` directly or use Login's Create a new account button. Registration
creates an in-memory Candidate demo account, displays a success confirmation and
returns to Login when Go to sign in is selected. The new username or email and
password work until the tab is refreshed or closed. Use invented details and a
demo password. Registration sends no email and does not contact a server.

To preview the registration-unavailable state, optionally copy `frontend/.env.example`
to `frontend/.env.local`, set `VITE_DEMO_REGISTRATION_ENABLED=false`, then restart
Vite. Run the copy command from the repository root:

```powershell
Copy-Item frontend/.env.example frontend/.env.local
```

The default is enabled. This switch controls the frontend demonstration only.

Run the frontend checks from `frontend/`:

```powershell
npm test
npm run build
npm run preview
```

Preview serves the production build at `http://127.0.0.1:4173`. These tests cover
input validation, mock login, logout and remembered-identifier storage. Browser
verification should also cover submission by Enter, password visibility, loading,
failed login, direct dashboard navigation, refresh, and mobile overflow.

To inspect `/403`, sign in and select Preview access denied on the temporary
dashboard. Use the same tab: demo sessions are held in memory. Direct entry
without a session and refreshing `/403` return to Login. Both recovery buttons
return to the temporary demo dashboard. No real permission checks occur.

Open `/404` or an unknown path to inspect Page Not Found while signed out. After
sign-in, use Preview page not found on the temporary Dashboard to inspect the
signed-in state in the same tab. Go Back returns only to a permitted implemented
route, with Login or Dashboard as the fallback. Refresh leaves the public 404
page visible but clears the demo session and changes the recovery action to Sign in.

To inspect C01, sign in and select Browse published jobs on the temporary
Dashboard. `/candidate/jobs` provides eight current Published mock jobs, local
search, category/location/type filters, sorting and six-card pagination. Refresh
clears the demo session and returns to Login. My simulations opens the C07 case list.

Set `VITE_DEMO_JOBS_SCENARIO=empty` or `error` in `frontend/.env.local` and restart
Vite to inspect the empty-list or retryable failure state. Use `normal` or remove
the setting to restore the default list. Error retry repeats the configured
failure. See [Published Jobs](frontend-published-jobs.md) for implementation and
verification details.

Select View Details on a job card to inspect C02 at `/candidate/jobs/:jobId`.
The page shows public description, requirements and the current Published
version, with Back to Jobs navigation. Start Simulation opens the C03 upload
demo after creating a memory-only case. Set `VITE_DEMO_JOB_DETAIL_SCENARIO=unavailable` or `error`
in `frontend/.env.local` and restart Vite to review recovery states; use `normal`
to restore details. See [Job Detail](frontend-job-detail.md) for scope and checks.

C03 accepts one unencrypted PDF up to 10 MB and 20 pages. The browser checks it
locally; Upload and Extract runs simulated stages and shows fixed sample facts,
not facts read from the PDF. Cancel and Delete removes the demo case after
confirmation and returns to My simulations. Review Extracted Facts opens C04 after the
demo extraction completes. Refresh and
sign out clear all demo cases. No real upload or extraction occurs.

Set `VITE_DEMO_CASE_CREATION_SCENARIO=error` or `unavailable` to inspect creation
failures. Set `VITE_DEMO_EXTRACTION_SCENARIO=retryable`, `final` or `upload-error`
to inspect processing failures. Put these settings in `frontend/.env.local` and
restart Vite; use `normal` for the default flow. See
[CV Upload](frontend-cv-upload.md) for state behavior and verification.

C04 lets you add, remove and correct professional facts, inspect short mock
evidence and save changes. Confirm Facts validates required values, requires a
review acknowledgement and locks the case after success. Continue to Simulation
opens C05. There is no raw PDF preview or identity editor. Optional research
details remain hidden unless the demo capability is enabled.

Set `VITE_DEMO_REVIEW_SCENARIO=missing`, `empty`, `load-error`, `save-error` or
`confirm-error` in `frontend/.env.local` and restart Vite for review states. Use
`VITE_DEMO_RESEARCH_ATTRIBUTES_ENABLED=true` to inspect optional demo research
details. Restore `normal` and `false` for the default flow. See
[CV Fact Review](frontend-cv-review.md) for scope and checks.

C05 provides Run Simulation, three method statuses, a local event log, completion
and failure states. Use `VITE_DEMO_SIMULATION_SCENARIO=retryable` or `final` in
`frontend/.env.local` and restart Vite to inspect failures; restore `normal`
afterwards. View Results opens C06 and My simulations opens C07. Reload or
sign out clears the demo case. See [Simulation Progress](frontend-simulation-progress.md).

C06 displays three automated example outputs and two primary human slots, with
Summary, Detailed Comparison and Explanations tabs. Refresh Human Results stays
on the page; deletion requires confirmation and returns to My simulations.
Use `VITE_DEMO_RESULTS_SCENARIO` for `unassigned`, `submitted`, `human-update`,
`load-error`, `refresh-error` or `delete-error`, then restart Vite. Restore
`normal` afterwards. See [Simulation Results](frontend-simulation-results.md).

C07 lists owned retained cases from the current tab. My simulations opens the
list, Continue selects the correct workflow stage, and View Results opens C06
after completion. Delete uses confirmation; expired/deleting cases are hidden.
Use `VITE_DEMO_HISTORY_SCENARIO=error` or `delete-error` and restart Vite for
failures; restore `normal` afterwards. See [My Simulations](frontend-my-simulations.md).

Login, Register, Access Denied, Page Not Found, Published Jobs, Job Detail, CV Upload, CV Fact Review, Simulation Progress, Simulation Results, My Simulations and a temporary navigation target are implemented. See
[frontend Login](frontend-login.md) and [frontend Register](frontend-register.md)
for their structure and scope, [Access Denied](frontend-forbidden.md) for P03,
and [Page Not Found](frontend-not-found.md) for P04.

To inspect H01, sign in with `hr` or `hr@simrecrut.test` and
`SimRecrut123!`, then choose Manage HR jobs on the temporary dashboard.
`/hr/jobs` lists eight managed mock jobs with search, Draft/Published filters,
newest/oldest sorting and pagination. Candidate accounts are directed to Access
Denied for this route. Create New Job and Edit Draft open H02; version history
opens H03. See [HR Job Wizard](frontend-hr-job-wizard.md) for its seven-step flow.
Use `VITE_DEMO_HR_WIZARD_SCENARIO=load-error`, `save-error`, `extraction-error`,
`validation-error`, `publish-error` or `conflict` to inspect recovery; restart Vite
after changing and restore `normal` afterwards.
Set `VITE_DEMO_HR_JOBS_SCENARIO=empty` or `error` in `frontend/.env.local` and
restart Vite for alternate list states; restore `normal` afterwards.
See [HR Jobs](frontend-hr-jobs.md) for scope and verification.

H03 Versions opens the managed job history and read-only configuration tabs.
New Draft copies a published version into H02 only when no editable draft exists.
H02 publication success links back to the updated history.
Use `VITE_DEMO_HR_VERSIONS_SCENARIO=empty`, `load-error`, `detail-error` or
`clone-error` and restart Vite for alternate states; restore `normal` afterwards.
See [HR Job Versions](frontend-hr-job-versions.md).

H04 Case Results in the HR sidebar opens `/hr/results`. This is an authorised
mock-case list with job/query filters, local search, sort and pagination.
Open Case opens H05. Use `VITE_DEMO_HR_CASES_SCENARIO=empty`
or `error` and restart Vite for alternate states; restore `normal` afterwards.
See [HR Authorized Cases](frontend-hr-cases.md) for scope and verification.

H05 `/hr/results/:caseId` shows a read-only professional snapshot, recorded
versions, matching contributions and five outcome slots. Back to Results retains
the jobId query; Refresh rereads permitted details. Set
`VITE_DEMO_HR_DETAIL_SCENARIO=error` or `blind` and restart Vite to inspect recovery
or hidden-result projection; restore `normal` afterwards. See
[HR Case Details](frontend-hr-case-detail.md).

To inspect the integrated R01-R06, sign in with `researcher` or
`researcher@simrecrut.test`, password `SimRecrut123!`. Choose Browse research
experiments. Create New Experiment opens seven required settings; creation
opens a Draft's detail tabs without execution. Select and save subjects, create
demo pairs, run local validation, inspect Pending reviews and open unavailable
metrics. Research Subjects and Models expose read-only records and checked
selection actions. All Research data is independent of Candidate uploads.
Use `VITE_DEMO_RESEARCH_EXPERIMENTS_SCENARIO=empty` or `error` in
`frontend/.env.local` and restart Vite for alternate states; restore `normal`
afterwards. See [Integrated Research frontend](frontend-research-module.md).

V01 `/reviewer/tasks` is available to `hr` and `researcher` with password
`SimRecrut123!` through My Review Tasks on the dashboard or workspace sidebar.
Each sees only their assigned authorised tasks. R03/R04 assignments enter the
target recipient list. Open Task enters V02 details and submission.
Use `VITE_DEMO_REVIEW_TASKS_SCENARIO=empty` or `error` in `frontend/.env.local`
and restart Vite for alternate states; restore `normal` afterwards. See
[My Review Tasks](frontend-my-review-tasks.md).

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

## Review task details and submission

V02 `/reviewer/tasks/:reviewId` opens from V01 Open Task. Sign in as `hr` or
`researcher` with `SimRecrut123!`. Initial task 001 supports a Standard review;
002 supports an instructed-bias demo; 003 illustrates your locked submission.
Choose Accept/Reject, enter a 1-2000 character reason, then confirm submission.
Cancel keeps input; confirmation locks the result and updates the list.
`VITE_DEMO_REVIEW_DETAIL_SCENARIO=load-error` or `submit-error` exercises errors.
Restore `normal` and restart Vite. Data remains memory-only; reload signs out.
See [Review Task Details](frontend-review-task-detail.md) for scope and checks.

## Admin user management

A01 `/admin/users` uses the reserved `admin` / `admin@simrecrut.test` login with
password `SimRecrut123!`. Choose Manage users on the temporary dashboard.
Search/filter accounts, open metadata details, add/edit mock entries and confirm
Enable/Disable. Changes affect only the memory directory, not demo login accounts
or case grants. Logout/reload resets changes. Roles & Permissions opens A02.
Use `VITE_DEMO_ADMIN_USERS_SCENARIO=empty`, `load-error`, `save-error` or
`status-error` in `.env.local` and restart Vite for alternate states; restore
`normal` afterwards. See [User Management](frontend-admin-users.md).

## Admin roles and permissions

A02 `/admin/roles` uses the existing Admin login (`admin` / `SimRecrut123!`).
Enter Roles & Permissions from A01 or the Administration sidebar. Edit a role's
mock description/menu/action tree, save or cancel, then inspect the permission
matrix. Assign Role updates the mock account directory and its user counts;
Users returns to A01. The matrix and preview do not alter application routes,
login credentials or case grants. Reviewer remains an HR/Researcher responsibility.
Set `VITE_DEMO_ADMIN_ROLES_SCENARIO=empty`, `load-error`, `save-error` or
`assign-error` in `.env.local` and restart Vite to check recovery; restore normal.
See [Roles and Permissions](frontend-admin-roles.md).

## Admin model versions and release

A03 `/admin/models` uses `admin` / `SimRecrut123!`. Choose Model Versions in
Administration navigation. View metadata, register a predefined offline demo
package as TESTED, approve it, then activate it separately. Retire requires an
APPROVED same-type replacement. All evidence/readiness and slot changes are
synthetic. No real model files, training, inference or release operation is used.
Existing Research/Candidate model examples remain independent. Logout/reload
resets the registry. Set `VITE_DEMO_ADMIN_MODELS_SCENARIO=empty`, `load-error`,
`register-error` or `release-error` in `.env.local` and restart Vite for alternate
states; restore normal. See [Model Versions and Release](frontend-admin-models.md).

## Admin frozen protocols

A04 `/admin/protocols` uses `admin` / `SimRecrut123!`. Open Frozen Protocols
from the Administration sidebar. Filter summaries, refresh the list and view
read-only details. Missing frozen configuration is explicitly unavailable.
There are no protocol modification actions or real API requests. Set
`VITE_DEMO_ADMIN_PROTOCOLS_SCENARIO=empty`, `load-error` or `detail-error`
and restart Vite for alternate states; restore normal afterwards.
See [Frozen Protocols](frontend-admin-protocols.md).

## Admin service health

A05 `/admin/system-health` uses `admin` / `SimRecrut123!`. Open Service Health
from Administration navigation. Runtime status remains Unknown until a real
health contract is connected. Refresh reloads the local summary; Models opens
A03. View Failures opens A06. Set `VITE_DEMO_ADMIN_HEALTH_SCENARIO=empty`,
`load-error` or `stale` and restart Vite for alternate states; restore normal.
See [Service Health](frontend-admin-health.md).

## Admin failed tasks

A06 `/admin/failures` uses `admin` / `SimRecrut123!`. Open Failed Jobs & Errors
from Administration navigation or View Failures in A05. Filter synthetic tasks,
check date validation and open read-only technical details. Retry is disabled
until a supported Admin retry operation is implemented; Audit opens A07.
Set `VITE_DEMO_ADMIN_FAILURES_SCENARIO=empty`, `load-error` or `detail-error`
and restart Vite for alternate states; restore normal afterwards.
See [Failed Jobs and Errors](frontend-admin-failures.md).

## Admin audit logs

A07 `/admin/audit` uses `admin` / `SimRecrut123!`. Open Audit Logs from the
Administration sidebar or Audit in A06. Search applies draft filters; Refresh
and pagination retain the last applied query. View opens safe read-only details.
Events are independent fixtures, not a real audit trail. Set
`VITE_DEMO_ADMIN_AUDIT_SCENARIO=empty` or `load-error` and restart Vite to
check alternate states; restore normal. See [Audit Logs](frontend-admin-audit.md).
