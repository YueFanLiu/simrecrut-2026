# CV library and simulation navigation

This change is frontend-only. It does not replace the case upload or DeepSeek
pipeline with the library's local extraction implementation.

## Implemented journeys

- Library → Use for a simulation → published jobs → job detail → case CV
  selection, with the original library CV preselected.
- Job → case CV selection → confirmed library CV → existing `/resume` endpoint
  → job-specific review, without another PDF upload or extraction.
- Case CV selection → unconfirmed library CV → library review → confirm and use
  for this simulation → job-specific review. The already-owned case ID survives
  library navigation; no extra case is created.
- Case CV selection → upload a new CV → original case upload/extraction route.
  The user can switch back to the saved-CV picker before uploading.

Only resource IDs cross through URL queries. Existing server ownership,
permission, retention and immutable-snapshot checks remain authoritative.
Local selectors exclude unconfirmed and expired records. Library load errors
have a visible retry instead of being presented as an empty library.

Attachment preflights case status. Network retries reuse an operation key for
the same case/CV pair. If an earlier request already advanced the case, the UI
continues from its saved step instead of applying a different CV. A 409 also
rechecks that step before reporting a conflict.

Library confirmation is sufficient for case reuse; cloud publication is not an
attachment prerequisite. The contextual "Confirm and use" action confirms the
CV and attaches the reviewed facts. Publication remains available separately;
the original standalone library confirmation/publication action is preserved.

## Extraction status recovery

A temporary status-read failure preserves the last known case and extraction
state, shows a retry action, and leaves automatic polling active. Initial-load
failures have a dedicated retry panel instead of claiming the case is missing.
HTTP 401/403/404/410 still clear the case. The component regression tests cover
initial failure, recovery into review and definitive loss of access.

## Backend work deliberately left for the extraction owner

1. Provide a shared extraction entry point or align library extraction with the
   existing DeepSeek case service, including the frozen catalogue/version.
2. Add a supported operation that saves a confirmed case's structured profile
   to the owned CV library without retaining or reuploading its original PDF.
3. Return the library source ID/display name on case status, so the client can
   show the exact source after navigation or a recovered response.
4. Verify full profile/supplement retention, independent case snapshots, expiry,
   original deletion, library deletion and already-created case behaviour.

Until these endpoints exist, a newly uploaded case PDF stays on the existing
DeepSeek case path and is not automatically added to the library. The picker
states this explicitly. The demo's memory-only extraction workflow is unchanged.
