# Standardized connected resume workflow

The connected resume flow follows the supplied technical specification's upload, extraction, fact review, confirmation, retention and deletion rules. RuoYi remains the authentication and permission layer. The existing job/scoring/research pages still run as separate demonstrations; this change does not invent model results or connect new case workflows.

## State transitions

`UPLOADED -> EXTRACTING -> REVIEW_REQUIRED -> CONFIRMED`

An unreadable file reaches `FAILED` and supports explicit retry. Confirmation locks facts and freezes the assessment reference month. Saving a draft does not confirm or extend its original expiry. Cloud publication requires confirmation; its failure leaves the confirmed snapshot valid. An inaccessible `PURGING` record is cleaned until completion. Old SQLite `IMPORTED` and `PROCESSING` labels migrate to the standard names. Interrupted extraction is marked failed after a restart and can be retried; automatic durable worker resumption is not implemented.

## API and repeat operations

The frontend uses `/api/v1/resumes` exactly once, through the RuoYi-authenticated Spring Boot service. Authentication endpoints retain their existing RuoYi paths. Business responses contain `code`, `message`, `requestId`, and `data`; success is zero. Authentication errors follow the same envelope, and protected responses use `Cache-Control: no-store`. Existing `/simrecrut/resumes` endpoints remain compatibility routes.

Mutation requests require an `Idempotency-Key` of 16 to 128 ASCII letters, digits, underscores or hyphens. Keys, canonical payload hashes and small operation responses are saved in the authenticated user's index for 24 hours. Repeating a completed request reuses its response; a changed action, target or payload under the same key is rejected. A failed operation can be retried with the same key. Explicitly starting another extraction attempt uses a new key. Cached responses always recheck ownership and expiry; deletion clears other target-linked responses, and an old import response cannot restore a deleted record.

Draft saves use `/{id}/save` with `confirmed=false`; confirmation uses `/{id}/confirm` with `confirmed=true`. Both include the loaded `expectedRevision`. A stale editor is rejected. Publication and cleanup use their respective endpoints. Delete is authenticated and idempotent; retries for a purging or already deleted own record return its deletion outcome without exposing content.

Confirmation requires a review draft. Manually corrected or added facts are marked `USER_SUPPLIED` by the server; unchanged extracted facts retain their original provenance. Re-uploading an existing document opens the existing review rather than overwriting its manual corrections.

## Browser behavior

The connected review page polls extraction every two seconds and stops at terminal states or page exit. Reload reads backend state. Changing records aborts the old read and clears its facts; a late response cannot replace the new record. Permission/load failures clear protected facts. Saving a draft remains separate from confirmation. The combined confirmation/publication action waits for confirmation before publishing and retains the server's confirmed state on publication failure. No Run button is exposed until actual scoring is integrated.

## Remaining full-spec integration

This is the existing resume adapter, not the complete job-linked case service. Published job selection, case creation and `AWAITING_UPLOAD`, optional research-value transactions, durable MySQL worker claims, actual three-method scoring, case-dependent experiment cleanup, and revised completion-based deadlines still require their business integrations. They must follow this contract when connected.

## Verification on 2026-10-12

Java 17 Maven verification passed 17 tests. All 148 frontend regression tests passed and the production build succeeded. The 17 new live contract checks and 25 existing compatibility/authentication checks passed against the running RuoYi/Aiven services. They cover replay, payload conflicts, stale revisions, protected access, confirmation, publication and deletion. Synthetic test records were removed.
