# WANG frontend reference import

The current checkout is the local `yuefan` branch, tracking `origin/yuefan`.
Existing uncommitted frontend files were retained without modification.

The source snapshot under `frontend/reference/wang/` comes from:

- Repository: https://github.com/YueFanLiu/simrecrut-2026
- Branch: `WANG`
- Commit: `dbd345cd4b2816ffbd02d06d84b111ae4812d719`

The snapshot preserves 30 frontend files byte for byte, including six Research
views, shared components, fixtures, workspace state, route metadata, styles and
the original frontend entry/configuration files. Backend files and the separate
resume workspace were not imported. `source-manifest.json` records the source
commit and SHA-256 of each imported file.

## Research views

| Unit | Imported path under `frontend/reference/wang/` |
| --- | --- |
| R01 | `src/views/research/ExperimentList.vue` |
| R02 | `src/views/research/ExperimentCreate.vue` |
| R03 | `src/views/research/ExperimentDetails.vue` |
| R04 | `src/views/research/ResearchSubjects.vue` |
| R05 | `src/views/research/ResearchModels.vue` |
| R06 | `src/views/research/ResearchMetrics.vue` |

## Integration boundary

This is an unmodified reference copy, not an active application or a merged
branch. The active frontend still uses `frontend/src/`, its existing npm
dependencies, routes, layouts and Pinia stores. Its previous R01 is archived under `frontend/reference/previous-r01/`.
Adapted copies of all six Research pages are now registered. See
[Integrated Research frontend](frontend-research-module.md) for review findings,
repairs and current behavior. The snapshot itself remains unmodified.

The imported workspace uses a provider, its own route metadata and global
styles. It also references `store/modules/user` and `router/permissions`, which
are absent from the imported branch's frontend tree. The active adaptation resolves
those dependencies using the existing demo session and guarded routes; English
content, scope and functional requirements have been checked. Do not replace the active App, main, router or package files wholesale.

No dependency installation, backend import, commit or push was performed.
The active frontend production build and source preservation checks pass.
Source hashes are verified against the recorded Git commit.
