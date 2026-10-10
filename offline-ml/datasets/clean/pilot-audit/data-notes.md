# Local pilot review

Keep this folder local. Only this small note belongs in Git; evidence, mappings,
decisions, renders and verification outputs are ignored.

Start with `owner-confirmation-questions.md` and `mapping-review.md`. Pending
choices and later owner answers stay in that one questions file.

- `evidence/`: 23 CV source audits, 20 job audits, exact JSONL spans and 45 rendered
  PDF pages. Twenty CVs are provisionally usable after three held originals.
- `mappings/`: original CV/job vocabulary proposals and the combined draft JSON.
  None is an approved matching or demographic table.
- `verification/`: the 43-item source checklist, independent audit, acquisition
  checks, source receipts, Gantt evidence and the preparation-readiness summary.
- `extraction/`: private structured drafts, original/redacted text, bounded
  failure receipts, source-specific run records and reusable provider replies.
- `tools/`: local source-specific audit builders and checks. Run them from the
  repository root. General cleaning/training code remains under `offline-ml/src/`.

The maintained acquisition/review procedure is in
[Dataset preparation](../../../../docs/research/dataset-preparation.md). Training
controls and remaining protocol steps are in
[Pilot review and PyTorch preparation](../../../../docs/research/pilot-review-and-training.md).
The unchanged Lark Markdown snapshot in `docs/research/` is the original source;
do not keep competing full-text copies in temporary folders.
