# Revised CV copies: frontend and proposed backend contract

The library and confirmed-CV review offer "Edit a revised copy". The editor
starts from a deep copy of the seven structured profile groups, including
personal/photo data and free-text supplements. The original remains locked.
No PDF is reuploaded, regenerated or retained to bypass content deduplication.

## Current availability

The existing backend has no copy/revision creation endpoint. Consequently,
`VITE_APP_CV_REVISIONS_ENABLED` defaults to false and the server save button is
disabled. The editor explicitly labels the copy as not saved to the library.
Users can prepare edits, download the draft to their device and restore it
later against the same source ID and revision. No draft contents are written
to browser local/session storage. Downloaded drafts include the copied personal
information and photo, but no original PDF bytes.

Enable the flag only after implementing and validating the contract below.
404/405/501 responses still produce an unavailable-service message while keeping
the edits on screen. An ambiguous save retains the exact payload and operation
key, pauses edits, and offers an idempotent retry. Leaving/refreshing with edits
or an unknown save result prompts the user.

## Proposed operation (not implemented by this frontend change)

`POST /api/v1/resumes/{sourceResumeId}/revisions`

Headers: authenticated session and `Idempotency-Key`.

Request:

```json
{
  "displayName": "CV for IT — revised",
  "expectedRevision": 2,
  "draft": {
    "professional": {"personal": [], "skills": [], "experience": [], "education": [], "languages": [], "projects": [], "other": []},
    "warnings": []
  }
}
```

Return a standard API success envelope whose `data` includes:

```json
{"id":"cccccccccccccccccccccccccccccccc","status":"REVIEW_REQUIRED","confirmed":false}
```

The frontend rejects a response pointing at the original ID, an already-confirmed
copy or an unexpected state. After a valid response it opens the new record in
the normal review page, preserving any pending simulation case ID. Saving a copy
never confirms or attaches it to a simulation automatically.

## Required backend semantics for the owner

- Recheck account ownership, current permissions, confirmed source, expiry and
  expected revision, including on idempotent replay. At minimum the frontend
  requires the existing resume upload and edit grants before enabling save.
- Atomically create a new owned library record with its own ID, editable facts,
  retention timestamps and a source-parent reference. Do not alter the confirmed
  source, its publication, or immutable snapshots in existing cases.
- Copy no PDF path or raw text and set `originalAvailable` to false. Do not use
  the original file's unique content hash to collapse the copy back onto its parent.
- Validate bounded draft fields and set server-owned correction provenance. Keep
  unchanged evidence only where it still supports the copied fact.
- Leave the copy unconfirmed; normal review/confirmation and reuse happen later.
- Replay the same operation key/payload to the same new ID. Reject reuse of a key
  with a different payload. A source conflict, deletion or expiry must not partially
  create or modify a record.
- Cover photo/contact/supplement preservation, independent deletion/expiry, failed
  saves, ambiguous response recovery and existing-case immutability in integration tests.

Local draft files are versioned and bounded to 2 MiB. Restoration checks the format,
source identity/revision and profile shape. Name, source ID and revision are
validated again when constructing a save request. Source transport metadata,
confirmation, publication and case state are excluded from the request.

## Restore concurrency

While a local draft file is read, editing, saving, exporting and further restores are disabled. The restore checks the page/source generation and any uncertain or completed save again after the asynchronous read; a stale read cannot replace a pending save payload. Read/parse failures unlock the unchanged draft. Unsubmitted shared-editor input must be submitted or cancelled before revision save, restore or export. These guards apply independently of the revision API feature flag.
