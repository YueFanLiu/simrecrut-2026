# Page ownership and maintenance

Each feature page has its own file. Coordinate shared component or session-state changes with the other contributors.

| Page | File in src/views |
| --- | --- |
| R01 | research/ExperimentList.vue |
| R02 | research/ExperimentCreate.vue |
| R03 | research/ExperimentDetails.vue |
| R04 | research/ResearchSubjects.vue |
| R05 | research/ResearchModels.vue |
| R06 | research/ResearchMetrics.vue |
| V01 | reviewer/ReviewTasks.vue |
| V02 | reviewer/ReviewTaskDetails.vue |
| A01 | admin/AdminUsers.vue |
| A02 | admin/AdminRoles.vue |
| A03 | admin/ModelReleases.vue |
| A04 | admin/FrozenProtocols.vue |
| A05 | admin/ServiceHealth.vue |
| A06 | admin/ProcessingFailures.vue |
| A07 | admin/AuditLogs.vue |

## Shared boundaries

`WorkspaceNavigation.vue` owns common navigation. `RecordTable.vue` owns the shared searchable list, statuses and row actions. `RecordDrawer.vue` owns confirmation and detail panels. Page components consume the provider from `state/workspace.js` through `useWorkspace()`; they do not create separate copies of session records. App.vue creates a fresh provider for each application instance.

The list-page files compose RecordTable intentionally. Change the corresponding page file for page-specific sections, and the shared table for behavior needed by all lists. Route additions require updates to `router/pages.js` and `router/pageComponents.js`. Demo fixtures stay in `api/demoFixtures.js`.

## Standalone CV workspace

| Responsibility | File under public/resume-workspace |
| --- | --- |
| DOM helpers, labels and local state | core/dom.js |
| Java API requests and action error handling | services/client.js |
| Database connection form | views/settings.js |
| Existing CV list and search | views/library.js |
| C03 upload and file validation | views/upload.js |
| C04 extraction status, evidence and confirmation | views/review.js |
| Professional fact form editor | views/factEditor.js |
| Initialization and upload-summary wiring | app.js |
| Page markup and script order | index.html |
| Existing shared styles | style.css |
| Prototype design styles | prototype.css |

The standalone adapter remains separate from the Vue application. It requires the Java 17 server on port 8765; Vite serves the research application on port 5173. Do not open the standalone asset HTML through Vite because its API routes and local token are supplied by Java.
