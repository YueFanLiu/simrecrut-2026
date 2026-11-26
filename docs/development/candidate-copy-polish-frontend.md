# Candidate wording and interaction polish

The sign-in preference now says **Remember my username or email** and explains that the option saves that identifier in the browser, never the password. Its native checkbox has a visible label, keyboard focus indicator and linked description. The storage helper and existing session/token behavior are unchanged. Registration privacy notices use the same wording.

The simulation steps now read **Choose CV → Review CV → Run Simulation → View Results**, covering both library reuse and a new upload. Review headings, acknowledgements and draft notices describe all displayed CV information, including personal information and free additions. Professional-only labels remain where they describe scoring or backend validation. The library distinguishes editable drafts from confirmed versions; publication retains its existing behavior and explicitly excludes the Personal information section.

Destructive simulation actions say **Delete simulation**, use a danger style and still require the existing confirmation dialog. The safe choice says **Keep simulation**. No deletion endpoint or deletion timing changed.

Library review displays an unsaved-changes notice. **Reload saved CV** asks before replacing edited facts; **Refresh PDF status** still updates metadata only. Leaving the review or switching CVs also asks before discarding changes, including unfinished local skill edits. Browser refresh/close uses the native unsaved-change warning. In-flight writes block ordinary navigation, while verified deletion/attachment success can continue to its destination. Long action labels wrap within the review panels, and new messages have Chinese and French translations.

No new backend APIs, model logic, skill scoring, storage behavior, assets or brand colors were introduced. Existing frontend tests/build and local browser checks cover wording, translated layouts, checkbox behavior and the reload/keep-editing interaction. Real storage, account and extraction integration remain separate backend checks.
