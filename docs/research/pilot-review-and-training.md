# Pilot review and PyTorch preparation

The source audit is complete. The PyTorch feature encoder, grouped splitter,
network and development runner are prepared. Real-data training still needs
human fact reviews, compatible pairs and a frozen label protocol. Tests use
synthetic inputs; they do not establish dataset quality or hiring accuracy.

This work follows [Lark revision 221](https://ijppwkmdd5yg.jp.larksuite.com/docx/UGOYdlnb5or8prxDdtrjKzyvpwd)
and the [unchanged source snapshot](lark-specification-source.md). The source
was checked again on 2026-10-10. Re-read its relevant sections before changing
cleaning, training or evaluation, and refresh the snapshot if Lark changes.
Follow the [coding and comment rules](../development/coding-standards.md).
MouZheng LI owns this offline work.

## Source audit

All original 20 CVs and 20 jobs were checked against their raw source rows.
Every original CV page was rendered and inspected. Three CVs were held because
of a non-IT category mislabel, copied template content, or unresolved technical
scope. Three additional CVs were inspected as replacements accepted by the owner. The
original export remains unchanged.

The audit contains 23 CVs, 45 inspected PDF pages and 20 provisionally usable
technical IT CVs. All inspected pages have readable embedded professional text;
none needed OCR. All 20 jobs matched their publisher rows. Evidence references
retain exact source offsets, file hashes, source IDs and the source revision.

All facts and mappings remain `HUMAN_APPROVAL_REQUIRED`. Automated source
inspection does not count as a human approval or the two-reviewer calibration.
Missing language levels, uncertain degree completion and conflicting dates
remain unresolved. The jobs have experience and language conflicts, alternative
qualifications, optional skills and employer-stack wording that HR must review.

MouZheng LI accepted preparation policies 1-5 on 2026-10-10, including pilot
replacements, conservative duration handling, initial job targets and mapping
rules. Individual qualification reviews and the calibration/effect/corpus
decisions 6-10 remain unfinished. The existing local questions file is the
decision record.

Local review files are under `offline-ml/datasets/clean/pilot-audit/`:

| File | Purpose |
| --- | --- |
| `evidence/` | Per-CV and per-job findings, exact JSONL evidence and rendered pages. |
| `mapping-review.md`, `mappings/` | Combined review table and machine-readable mapping drafts. |
| `owner-confirmation-questions.md` | Concrete decisions, starting with scope and source conflicts. |
| `verification/` | Requirement checklist, independent checks, source receipts and readiness. |
| `extraction/` | Private provider replies, source-bound drafts, failure history, caches and derived run receipts. |
| `tools/` | Local audit builders and verification scripts; these contain source-specific proposals. |

These files, raw datasets, cleaned records, rendered pages and outputs are
ignored by Git. Only small `data-notes.md` files and directory markers are
allowed in dataset folders. Keep source-derived mappings local. Names remain
in restricted original evidence and have no professional/model input slot.
Never infer gender or ethnicity from names, photos or schools.

This page owns the training protocol and current implementation status.
[Dataset preparation](dataset-preparation.md) owns acquisition, cleaning and the
review-import contract. The Lark snapshot is retained as original source evidence.
Pending choices and later answers have one home in the local questions file;
do not create another meeting note or approval copy in a temporary folder.

The draft tables have their own audit schema. They are not ready to feed into
`apply-review`; actual reviewers must resolve the questions and complete that
command's [approved review contract](dataset-preparation.md).

## Extraction and fact review

The source's Candidate journey step 6 and extraction sequence read embedded PDF
text first and use OCR only when that text is unusable. Redacted text then goes
to DeepSeek for facts in the specified structure. Backend validation checks the
JSON fields, types and allowed values, with one structured-output repair attempt.
The result is an evidence-backed review draft; confirmation precedes matching.
DeepSeek does not choose HR flags, weights, scores or Accept/Reject decisions.

The offline adapter now calls DeepSeek behind a replaceable provider interface.
A hash-bound local privacy review precedes external calls. The validator resolves
source excerpts to original offsets/pages, permits whitespace alignment only,
and rejects invented fields, unsupported wording or selected HR flags. Normalized
codes and reviewed durations remain unset at this extraction stage. Original
evidence, failures and call receipts stay inside the ignored pilot audit.

Every inspected pilot PDF has usable embedded text. PDF reading and the optional
OCR boundary are separate from the provider. Correct JSON does not establish
correct qualifications, resolve contradictory dates or prove completeness.
The [preparation guide](dataset-preparation.md) owns setup, commands, cache
behavior and the completed 40-record extraction cross-check. All 120 coverage
warnings and 23 held fields now have source-check proposals in the existing
local mapping review. These proposals do not approve facts or mappings. Real-data training still
requires the reviewed-data and frozen-label stages.

## Prepared training components

The [feature schema](../../contracts/feature-schemas/professional-research-v1.json)
fixes criterion and category order. `training/features.py` uses decimal values
and seven-place HALF_UP rounding before producing numeric features. HR weights
must sum exactly to one. It does not normalize invalid weights or fill unknown
facts. Each contribution is rounded before summation. Inactive criteria have
zero match and weight; an active criterion can have zero weight.

Both models receive 32 float32 values: five matches, five weights, five rounded
products, mandatory pass, and sixteen research positions. Neutral fills those
last positions with zeros. Biased uses the source's fixed one-hot categories.
Withheld zeros differ from the explicit unknown categories. Labels, bias effects,
biased scores, names and source text are excluded from inputs.

`training/splits.py` groups by reviewed original-person ID before variants and
keeps calibration people outside final test. The 500-person target gives
300/100/100 people. Cross-source identity resolution remains a reviewed data step.

`training/network.py` creates separate 673-parameter networks:
32 -> 16 -> 8 -> 1, with ReLU after each hidden layer and dropout 0.1 after
the first. The output is a raw logit. `training/engine.py` uses
[BCEWithLogitsLoss](https://docs.pytorch.org/docs/2.10/generated/torch.nn.BCEWithLogitsLoss.html)
so sigmoid is applied inside the training loss, and
[Adam](https://docs.pytorch.org/docs/2.10/generated/torch.optim.Adam.html)
with the recorded learning rate and weight decay.
Sigmoid scores reflect fit to synthetic labels; the sources supply no verified
individual hiring probabilities.

The [starting configuration](../../offline-ml/configs/training-start.json)
records learning rate 0.001, weight decay 0.0001, batch size 32, up to 100 epochs,
patience 10 and five matched seeds. The runner restores the lowest validation-loss
checkpoint. Validation disables dropout and gradients. Each model has its own
labels, parameters, optimizer and validation cutoff. There is no final-test
input or best-seed selection in this development runner.

The numerical partition checks reject person leakage, missing label classes,
malformed inputs and positive rule targets with failed mandatory conditions.
They do not certify human truth. Model predictions are not forced to obey the
rule; disagreement must be reported later. Validation cutoff selection saves
all tested candidates and compares balanced accuracy, then macro-F1. One optimal
interval uses its midpoint; disconnected equal optima remain unresolved until
the protocol supplies a tie policy. Metrics at 0.5 are also retained.

Runs record the settings and Python/PyTorch versions. The current runner uses
CPU and explicitly seeds initialization and batch ordering. PyTorch documents
[reproducibility limits across versions and platforms](https://docs.pytorch.org/docs/2.10/notes/randomness.html);
a seed alone is not a promise of identical results everywhere.

## Run the preparation check

Install the training and test extras in the local environment:

```powershell
./offline-ml/.venv/Scripts/python.exe -m pip install -e './offline-ml[dev,training]'
./offline-ml/.venv/Scripts/python.exe -m simrecrut_ml audit-readiness --audit-dir offline-ml/datasets/clean/pilot-audit --settings offline-ml/configs/training-start.json
```

The package pins PyTorch 2.10.0. The current environment was checked with Python
3.12.14 and PyTorch 2.10.0+cu128, running the synthetic tests on CPU. The inspection
command prints only aggregate counts and unmet prerequisites. It does not
upload, approve, score or train records. The library runner accepts already
prepared tensors; there is no raw-data training command.

## Before real-data training

1. Resolve the local questions and obtain actual profile/job and mapping reviews.
   Confirm compatible roles, assessed requirements, active criteria and weights.
   Record a fixed `assessmentReferenceMonth` in YYYY-MM form for the offline
   corpus. Legacy Current/Present periods need a supported endpoint; unknown
   endpoints cannot silently become today's date. Merge overlapping relevant
   intervals and calculate years as months divided by twelve. Do not add a
   reviewed duration again when it describes the same dated period.
2. Build reviewed compatible candidate-job pairs with low, middle and high
   professional matches. The field audit is separate from the target of 500
   base people and from rule-cutoff calibration.
3. Calibrate the rule cutoff on at least 20 development pairs with two independent
   human rubric reviewers blinded to the numeric score. Resolve disagreements,
   retain both classes, save all cutoff candidates, freeze the choice and check
   the five-percentage-point sensitivity window. Reserve these people from test.
4. Freeze the controlled effect table and label protocol. Unknown effects are zero;
   store effects to eight decimal places. The source's 0.70 and -0.08 are examples,
   not approved defaults. Research categories are controlled values, not inferred
   demographic facts. Use training/calibration mandatory-pass cases' absolute
   rule margins: the 25th, 50th and 75th percentiles define small, medium and large
   scales. Start the combined study at medium and check clipping and label flips
   before freezing magnitudes. Generate both models' labels before training.
5. Split by original person before variants and fit data-derived mappings on
   training only. Use an HR-reviewed baseline, equal active weights and a swap
   between the two largest unequal weights; omit duplicate or meaningless
   alternatives. One compatible job, three distinct weight settings and two
   study variants give up to 3,000 rows for 500 people. Verify own-label class
   support in all partitions. Run the
   limited validation search and five matched seeds, then freeze settings and
   each validation cutoff before opening final test.
6. Complete final evaluation and packaging: own-label and shared-neutral-label
   metrics, controlled score differences and decision flips, group denominators,
   unseen job/weight cases and logistic regression. The source calls for Fairlearn
   group counts, selection rates and TPR; retain undefined TPR with its reason.
   Declare Top-K capacity and compare selected composition with the original pool.
   Save the full schema,
   preprocessing/matching versions, source manifests, splits, settings, effects,
   thresholds, dependency versions, checksums and results. Human ratings remain
   separate evidence: the proposed 20-case, two-reviewer, two-condition study has
   80 ratings, with blinded answers and balanced condition order. It differs from
   rule-cutoff calibration. Final evaluation and release packaging are not implemented
   by the current development runner.

Use the [dataset verification commands](dataset-preparation.md#verify-a-change)
after a change. Inspect the source and explanations as well as test results.
