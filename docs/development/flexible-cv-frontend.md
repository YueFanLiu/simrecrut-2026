# Flexible CV review frontend

The CV library review and connected case review use `CvFactsEditor`. The demo
case review uses the same editor with an adapter that preserves its existing
per-field status and validation. No extraction, scoring, database or backend
endpoint changes are included.

## Compatibility with the current API

Existing assessed facts retain their original shape and codes. New free-text
skills and sections use the existing `professional.other` array. Each record
contains only `categoryCode`, `value`, and `evidence`, accepted by the current
`Facts` validator:

```json
{"categoryCode":"OTHER","value":"Additional skill: User interviews","evidence":{"text":"","page":null,"source":"USER_SUPPLIED"}}
```

```json
{"categoryCode":"OTHER","value":"CV section: Portfolio\nA design portfolio and case studies.","evidence":{"text":"","page":null,"source":"USER_SUPPLIED"}}
```

These headings are intentionally readable in the existing HR projection. The
editor interprets them only on user-supplied OTHER records, leaving extracted
other facts untouched. Both save paths preserve these records; the existing
library publication removes personal facts but keeps other information.

Free additions do not claim a catalogue mapping and do not affect current
assessment scores. Editing an assessed skill into a free-text skill removes
the original assessed claim. A case whose assessed category becomes empty still
requires acknowledgement under the existing protocol. The library backend
still requires at least one assessed fact for confirmation; additions can be
saved as a draft when that condition is unmet. The UI explains both limits.

Custom sections share the current limit of 100 other records and 2,000 characters
per value, including the heading. New skills are limited to 100 characters.
Newlines are not allowed in a skill or section heading. No CV contents are
persisted to browser local storage.

## Follow-up for extraction/backend ownership

- Return raw skill names and full role, organisation, school and project text,
  including information not mapped to a scoring catalogue.
- Expose inferred suggestions separately from extracted facts, with evidence
  and stable IDs. `CvSkillEditor` already has suggestion display and accept /
  dismiss events; connected pages do not fabricate suggestions when absent.
- Agree a versioned native profile schema, including arbitrary named sections,
  before replacing this compatibility representation. Migrate existing OTHER
  records without dropping their contents.
- Confirm the intended retention and HR visibility of additional information,
  and the treatment of unmapped content during assessment and research export.

Before enabling a new response contract, verify save/reload, confirmation,
library reuse, HR rendering and original-file deletion against the real service.
